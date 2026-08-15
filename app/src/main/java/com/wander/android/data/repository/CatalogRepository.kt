package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the album and artist pages read.
 *
 * Room first, always: an album you have opened before renders instantly and works offline. The
 * backend is asked afterwards to fill in what Room has not seen — a Navidrome album browsed for
 * the first time, or an artist whose later records were never fetched.
 *
 * Artists are keyed by **name**, not by id. Only Navidrome gives tracks a stable `artistId`;
 * YouTube Music rows carry none, so an id-keyed artist page would be empty for every streaming
 * source. The name is the one identifier every backend actually provides.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val musicRepository: MusicRepository
) {

    // ── Album ───────────────────────────────────────────────────────────────────────────────

    fun albumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId).map { it.map(TrackEntity::toUnifiedTrack) }

    suspend fun album(albumId: String): UnifiedAlbum? = withContext(Dispatchers.IO) {
        albumDao.getAlbumById(albumId)?.toUnifiedAlbum() ?: albumFromTracks(albumId)
    }

    /**
     * Pulls the album's tracks from its backend and persists them, so the flow above fills in.
     *
     * Reuses [MusicRepository.getAlbumTracks], which already marks an album browsed on your own
     * server as part of your library.
     */
    suspend fun refreshAlbum(albumId: String) {
        val album = album(albumId) ?: return
        musicRepository.getAlbumTracks(album)
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

    // ── Artist ──────────────────────────────────────────────────────────────────────────────

    fun artistAlbumsFlow(artist: String): Flow<List<UnifiedAlbum>> =
        albumDao.getAlbumsByArtistFlow(artist).map { it.map(AlbumEntity::toUnifiedAlbum) }

    /**
     * Deduplicated: the artist page is fed by a cross-source search, so the same song arrives once
     * from Navidrome and once from YouTube Music. [TrackDeduplicator] keeps the copy from the
     * lowest-priority source — your own files and your own server before anything streamed.
     */
    fun artistTracksFlow(artist: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByArtistFlow(artist)
            .map { entities -> TrackDeduplicator.deduplicate(entities.map(TrackEntity::toUnifiedTrack)) }

    /**
     * Fills in an artist Room only partly knows, by searching every configured backend for their
     * name and persisting the hits.
     *
     * A search, rather than a per-source artist endpoint: only Navidrome has one, and the point of
     * this page is that it works the same whichever backend the track came from. Results are
     * persisted as non-library by [MusicRepository.searchAllSources], so browsing an artist does
     * not silently grow the Library tab.
     */
    suspend fun refreshArtist(artist: String) {
        musicRepository.searchAllSources(artist)
    }

    /** Cover for the artist header: whichever of their records has one. */
    fun artistImage(albums: List<UnifiedAlbum>, tracks: List<UnifiedTrack>): String? =
        albums.firstNotNullOfOrNull { it.coverArtUrl }
            ?: tracks.firstNotNullOfOrNull { it.artworkUrl }

    /** Which backends this artist's known material came from, for the page's subtitle. */
    fun sourcesOf(tracks: List<UnifiedTrack>): List<SourceType> =
        tracks.map { it.source }.distinct().sorted()
}
