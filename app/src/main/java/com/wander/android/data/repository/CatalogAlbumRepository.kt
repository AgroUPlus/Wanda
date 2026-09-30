package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Album reads and the writes that keep Room caught up with what a backend has told us about one.
 *
 * Split out of [CatalogRepository], which had grown past the file-size cap mixing this with the
 * artist half — see [CatalogRepository]'s own "Album is keyed by id, artist by name" split.
 */
internal class CatalogAlbumRepository(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val musicRepository: MusicRepository
) {
    fun albumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId)
            .map { it.map(TrackEntity::toUnifiedTrack) }
            .flowOn(Dispatchers.Default)

    suspend fun album(albumId: String): UnifiedAlbum? = withContext(Dispatchers.IO) {
        albumDao.getAlbumById(albumId)?.toUnifiedAlbum() ?: albumFromTracks(albumId)
    }

    /**
     * Pulls the album's tracks from its backend and persists them, so the flow above fills in.
     *
     * Two paths, because there are two ways to arrive here. When Room knows the album,
     * [MusicRepository.getAlbumTracks] is used — it marks an album browsed on your own server as
     * part of your library, which is the right claim for a record you host yourself.
     *
     * When it does not, the album is resolved from its id prefix instead. That branch is not an
     * edge case: an album tapped in an artist's shelf has no `AlbumEntity` row *and* no tracks, so
     * [album] returns null for it, and this used to return here without ever asking the backend —
     * leaving every YouTube Music album opened from an artist page permanently empty.
     */
    suspend fun refreshAlbum(albumId: String) {
        val album = album(albumId)
        if (album != null) {
            musicRepository.getAlbumTracks(album)
        } else {
            musicRepository.getAlbumTracksById(albumId)
        }
    }

    /**
     * Writes album rows the app has seen but not browsed — the shelves on an artist's page.
     *
     * Without this the album screen has no header until its tracks land, and then only the one
     * [albumFromTracks] can reconstruct from them. Non-library, for the same reason the tracks
     * are: seeing a record on an artist page is not owning it.
     */
    suspend fun rememberAlbums(albums: List<UnifiedAlbum>) = withContext(Dispatchers.IO) {
        // Only records Room has never seen. `insertAlbums` replaces on conflict, and a tile off an
        // artist shelf carries no track count and no duration — writing it over a Navidrome album
        // that has actually been browsed would blank fields the library screen shows.
        val known = albums.mapNotNull { album -> albumDao.getAlbumById(album.id)?.let { album to it } }

        // Rows Room already has get their credit corrected if it has changed. Without this a bad
        // one was permanent — inserts skip known albums, so an album once filed under an artist
        // called "Single" stayed there however many times its artist's page was opened.
        for ((incoming, stored) in known) {
            if (incoming.artist.isNotBlank() && incoming.artist != stored.artist) {
                albumDao.updateAlbumArtist(stored.id, incoming.artist, incoming.artistId)
            }
        }

        val unknown = albums.filter { album -> known.none { it.second.id == album.id } }
        if (unknown.isEmpty()) return@withContext
        // Left non-library: a tile on an artist's page is a record you have looked at, and
        // filing those into the Library tab is what made it list every artist you ever opened.
        albumDao.insertAlbums(unknown.map { AlbumEntity.fromUnifiedAlbum(it, isLibrary = false) })
    }

    /**
     * An album Room knows the *tracks* of but has no row for — the usual case for YouTube Music,
     * whose album rows only arrive by browsing the library. Assembled from the tracks rather than
     * left blank, since every field the header needs is already on them.
     */
    private suspend fun albumFromTracks(albumId: String): UnifiedAlbum? {
        val tracks = trackDao.getTracksInAlbum(albumId).map(TrackEntity::toUnifiedTrack)
        val first = tracks.firstOrNull() ?: return null
        return UnifiedAlbum(
            id = albumId,
            source = first.source,
            title = first.album ?: first.title,
            artist = first.artist,
            artistId = first.artistId,
            coverArtUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
            songCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            year = first.year,
            genre = first.genre
        )
    }
}
