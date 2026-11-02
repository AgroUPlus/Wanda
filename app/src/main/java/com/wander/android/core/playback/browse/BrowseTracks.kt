package com.wander.android.core.playback.browse

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EpisodeProgressRepository
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.data.repository.SmartMixRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tracks behind each folder of the browse tree that lists tracks, read from Room.
 *
 * Shared by browsing and playing: what a car lists under a folder and what plays when one of its
 * rows is picked are the same read, so the queue is the list the driver was looking at.
 *
 * Nothing here goes to a backend. A car asks for these while the phone may well be offline, and
 * Room is what the app shows offline too.
 */
@Singleton
internal class BrowseTracks @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val libraryBrowse: LibraryBrowseRepository,
    private val smartMixes: SmartMixRepository,
    private val episodeProgress: EpisodeProgressRepository
) {

    /** [node]'s tracks, or null when [node] lists folders rather than tracks, or no longer exists. */
    suspend fun of(node: BrowseId.Node): List<UnifiedTrack>? = when (node) {
        BrowseId.Recent -> trackDao.getRecentlyPlayedTracks(SHELF_LIMIT).toTracks()
        BrowseId.MostPlayed -> trackDao.getTopPlayedTracks(SHELF_LIMIT).toTracks()
        BrowseId.Liked -> libraryBrowse.likedTracks(LibrarySort.RECENTLY_PLAYED, emptySet()).first()
        BrowseId.Downloads -> libraryBrowse.downloadedTracks(LibrarySort.DATE_ADDED, emptySet()).first()
        BrowseId.Podcasts -> episodeProgress.inProgress.first()
        is BrowseId.Mix -> smartMixes.getSmartMixes().firstOrNull { it.seedType == node.seed }?.tracks
        is BrowseId.Album -> trackDao.getTracksInAlbum(node.albumId).toTracks().takeIf { it.isNotEmpty() }
        is BrowseId.Playlist -> playlistTracks(node.playlistId)
        is BrowseId.Search -> search(node.query)
        BrowseId.Root, BrowseId.Home, BrowseId.Library, BrowseId.Playlists, BrowseId.Albums -> null
    }

    /** Library tracks matching [query] by title, artist or album, most played first. */
    suspend fun search(query: String): List<UnifiedTrack> =
        if (query.isBlank()) emptyList() else trackDao.searchTracks(query.trim(), SEARCH_LIMIT).toTracks()

    suspend fun track(trackId: String): UnifiedTrack? = trackDao.getTrackById(trackId)?.toUnifiedTrack()

    /** Where an in-progress episode was left, for starting it there; null for anything else. */
    suspend fun resumePosition(track: UnifiedTrack): Long? =
        if (track.isEpisode) episodeProgress.resumePosition(track.id) else null

    /**
     * A Wanda playlist's tracks in its own order.
     *
     * Only Wanda's own playlists are in Room. A Navidrome or YouTube Music playlist is fetched
     * from its server each time it is opened, so it is not offered to a car at all rather than
     * offered and then failing in a tunnel.
     */
    private suspend fun playlistTracks(playlistId: String): List<UnifiedTrack>? {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return null
        val ids = playlist.trackIds.split(',').filter { it.isNotBlank() }
        val byId = trackDao.getTracksByIdsChunked(ids).associateBy(TrackEntity::id)
        return ids.mapNotNull { byId[it]?.toUnifiedTrack() }
    }

    private fun List<TrackEntity>.toTracks() = map(TrackEntity::toUnifiedTrack)

    private companion object {
        /** Home's shelves are a glance, not an archive. */
        const val SHELF_LIMIT = 50
        const val SEARCH_LIMIT = 50
    }
}
