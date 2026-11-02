package com.wander.android.data.repository

import com.wander.android.core.database.dao.RemotePlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.entity.RemotePlaylistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Room's copy of each backend's playlists: the listing as last refreshed, and each playlist's
 * tracks as last opened. The library reads the listing from here, so a Navidrome or YouTube Music
 * playlist is still there — and still opens — with the server out of reach.
 */
internal class RemotePlaylistStore(
    private val dao: RemotePlaylistDao,
    private val trackDao: TrackDao
) {
    /** Every stored backend playlist, in the order each backend listed them. */
    fun observe(): Flow<List<UnifiedPlaylist>> = dao.observeAll()
        .map { rows -> rows.map(RemotePlaylistEntity::toUnifiedPlaylist) }
        .flowOn(Dispatchers.Default)

    /** [source]'s list as it just answered: what it no longer lists is forgotten. */
    suspend fun replace(source: SourceType, playlists: List<UnifiedPlaylist>) {
        dao.replaceSource(
            source,
            playlists.filter { it.source == source }.distinctBy { it.id }
                .mapIndexed { index, playlist -> RemotePlaylistEntity.from(playlist, index, trackIds = null) }
        )
    }

    /** The tracks [playlistId] was just opened with; its tracks must already be in `tracks`. */
    suspend fun rememberTracks(playlistId: String, tracks: List<UnifiedTrack>) {
        dao.setTracks(playlistId, tracks.joinToString(",") { it.id }, tracks.size)
    }

    suspend fun playlist(playlistId: String): UnifiedPlaylist? = dao.getById(playlistId)?.toUnifiedPlaylist()

    /** The tracks [playlistId] last opened with, or null when it never was opened here. */
    suspend fun tracks(playlistId: String): List<UnifiedTrack>? {
        val ids = dao.getById(playlistId)?.storedTrackIds() ?: return null
        val byId = trackDao.getTracksByIdsChunked(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.let(TrackEntity::toUnifiedTrack) }
    }

    companion object {
        /** Sources whose playlists live in Room already, in tables of their own. */
        val ROOM_NATIVE = setOf(SourceType.LOCAL, SourceType.AGRO)
    }
}
