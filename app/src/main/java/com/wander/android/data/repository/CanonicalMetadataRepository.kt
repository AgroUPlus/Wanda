package com.wander.android.data.repository

import com.wander.android.core.database.dao.CanonicalMetadataDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.CanonicalMetadataEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The catalogue's names for tracks this device holds, and the job of getting them onto the rows.
 *
 * Two steps rather than one write, because the two have different lifetimes. What the catalogue
 * said is a durable fact and is kept; what `tracks` currently displays is rewritten from the
 * backend on every library refetch. So the fact is recorded once and *re-applied* on every sync —
 * see [applyToLibrary] — instead of being written once and quietly lost the next time the source
 * is refetched.
 */
@Singleton
internal class CanonicalMetadataRepository @Inject constructor(
    private val canonicalDao: CanonicalMetadataDao,
    private val trackDao: TrackDao
) {

    /**
     * Records what the catalogue knows about [trackId], keeping only what improves on the row.
     *
     * Completing, never overwriting — the rules are [CanonicalMetadataMerge]'s, kept apart from
     * the database so they can be argued with in a test rather than against a Room fake.
     */
    suspend fun record(
        trackId: String,
        recordingId: String,
        title: String?,
        artist: String?,
        album: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext false
        val betterTitle = title?.takeIf { CanonicalMetadataMerge.improvesOnTitle(track.title, it) }
        val betterArtist = artist?.takeIf { CanonicalMetadataMerge.fills(track.artist, it) }
        val betterAlbum = album?.takeIf { CanonicalMetadataMerge.fills(track.album.orEmpty(), it) }
        if (betterTitle == null && betterArtist == null && betterAlbum == null) {
            return@withContext false
        }

        canonicalDao.upsert(
            listOf(
                CanonicalMetadataEntity(
                    trackId = trackId,
                    title = betterTitle,
                    artist = betterArtist,
                    album = betterAlbum,
                    recordingId = recordingId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
        true
    }

    /**
     * Puts every correction back onto its row, and answers how many rows changed.
     *
     * Run after each sync rather than only when a correction arrives, because a library refetch
     * between two syncs will have restored the source's own metadata. Rows whose values already
     * match are not written, so the steady state is a read and nothing else.
     */
    suspend fun applyToLibrary(): Int = withContext(Dispatchers.IO) {
        var applied = 0
        for (row in canonicalDao.getAllOnce()) {
            val track = trackDao.getTrackById(row.trackId)
            if (track == null) {
                // The row has left the library. The correction has nothing to correct and would
                // otherwise be re-read forever.
                canonicalDao.deleteFor(row.trackId)
                continue
            }
            val title = row.title ?: track.title
            val artist = row.artist ?: track.artist
            val album = row.album ?: track.album
            if (title == track.title && artist == track.artist && album == track.album) continue
            trackDao.setDisplayMetadata(row.trackId, title, artist, album)
            applied++
        }
        applied
    }
}
