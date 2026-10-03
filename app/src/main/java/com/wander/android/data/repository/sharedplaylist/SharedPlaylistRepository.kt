package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistRole
import com.wander.android.data.sources.agro.PlaylistVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the playlist screen needs to know about a shared playlist, beyond its tracks. */
data class SharedPlaylistState(
    val agroId: String,
    val ownerId: String,
    val visibility: PlaylistVisibility,
    val editAccess: EditAccess,
    val role: PlaylistRole,
    val syncState: SharedSyncState,
    /** Set when this account published it from that Wanda playlist; null when it is followed. */
    val localPlaylistId: String?
) {
    val isCollaborative: Boolean get() = editAccess != EditAccess.OFF

    /** What this account may do right now: nothing at all once it is no longer shared. */
    val effectiveRole: PlaylistRole get() = if (syncState == SharedSyncState.REVOKED) PlaylistRole.VIEWER else role
}

/** One track of a shared playlist and the item it is, so an edit can name the item. */
data class SharedTrack(val item: SharedPlaylistItemEntity, val track: UnifiedTrack)

/**
 * The shared playlists this device holds copies of: following and unfollowing them, and reading
 * them back for the screens. Every read is Room; the network only ever writes — see
 * [SharedPlaylistSync].
 */
@Singleton
class SharedPlaylistRepository @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val mirror: SharedPlaylistMirror
) {
    /**
     * Starts following [agroId] and keeps a copy, answering the id to open it by: the Wanda
     * playlist it was published from when it is this account's own, its `agro:` id otherwise.
     */
    suspend fun follow(agroId: String): Result<String> {
        dao.get(agroId)?.localPlaylistId?.let { return Result.success(it) }
        return api.follow(agroId).map { server ->
            mirror.write(server, api.me, localPlaylistId = null, state = SharedSyncState.SYNCED)
            SharedPlaylistMirror.routeId(agroId)
        }
    }

    /** Stops following and forgets the copy. Forgotten even when the server cannot be told. */
    suspend fun unfollow(agroId: String): Result<Unit> {
        val told = api.unfollow(agroId)
        dao.forget(agroId)
        trackDao.deleteUnreferencedUnresolved()
        return told
    }

    /** The shared state behind the playlist [playlistId] opens, whether followed or published. */
    fun observe(playlistId: String): Flow<SharedPlaylistState?> {
        val rows = SharedPlaylistMirror.agroIdOf(playlistId)?.let(dao::observe) ?: dao.observeForLocal(playlistId)
        return rows.map { it?.toState() }
    }

    /** The items behind [playlistId] in order, empty while it is not shared. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeItems(playlistId: String): Flow<List<SharedPlaylistItemEntity>> =
        observe(playlistId).flatMapLatest { state ->
            state?.let { dao.observeItems(it.agroId) } ?: flowOf(emptyList())
        }

    suspend fun agroIdFor(playlistId: String): String? =
        SharedPlaylistMirror.agroIdOf(playlistId) ?: dao.forLocal(playlistId)?.agroId

    /**
     * Where an edit to [playlistId] goes, when that is the shared copy. A published playlist whose
     * copy has not caught up yet is edited as the Wanda playlist it still is: the catch-up sends
     * it whole, where an edit to the empty copy would be written back over it.
     */
    suspend fun editTarget(playlistId: String): String? {
        SharedPlaylistMirror.agroIdOf(playlistId)?.let { return it }
        val copy = dao.forLocal(playlistId) ?: return null
        return copy.agroId.takeIf { copy.revision != SharedPlaylistEntity.UNSYNCED }
    }

    /**
     * The copy's tracks in order, one per item. An item whose track row has gone plays as the
     * metadata the server holds, so the list never shifts under an edit that names a position.
     */
    suspend fun tracks(agroId: String): List<SharedTrack> {
        val items = dao.items(agroId)
        val byId = trackDao.getTracksByIds(items.map { it.trackId }).associateBy { it.id }
        return items.map { item ->
            SharedTrack(
                item = item,
                track = byId[item.trackId]?.toUnifiedTrack() ?: UnifiedTrack(
                    id = item.trackId,
                    source = SourceType.UNRESOLVED,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationMs = item.durationMs
                )
            )
        }
    }

    /** The followed playlists, for the library and for the source that serves them. */
    suspend fun followed(): List<UnifiedPlaylist> =
        dao.followed().filter { it.myRole != PlaylistRole.OWNER.name }.map { it.toPlaylist() }

    /** Followed playlists this account may add to right now: the add-to-playlist sheet's extra targets. */
    suspend fun writableFollowed(): List<UnifiedPlaylist> =
        dao.followed().filter { row -> row.toState().effectiveRole.canAdd && row.myRole != PlaylistRole.OWNER.name }
            .map { it.toPlaylist() }

    suspend fun followedPlaylist(agroId: String): UnifiedPlaylist? = dao.get(agroId)?.toPlaylist()

    private suspend fun SharedPlaylistEntity.toPlaylist(): UnifiedPlaylist {
        val items = dao.items(agroId)
        val cover = items.firstNotNullOfOrNull { trackDao.getTrackById(it.trackId)?.artworkUrl }
        return UnifiedPlaylist(
            id = SharedPlaylistMirror.routeId(agroId),
            source = SourceType.AGRO,
            name = title,
            comment = description,
            coverArtUrl = cover,
            songCount = items.size
        )
    }

    private fun SharedPlaylistEntity.toState() = SharedPlaylistState(
        agroId = agroId,
        ownerId = ownerId,
        visibility = PlaylistVisibility.entries.firstOrNull { it.name == visibility } ?: PlaylistVisibility.PRIVATE,
        editAccess = EditAccess.entries.firstOrNull { it.name == editAccess } ?: EditAccess.OFF,
        role = PlaylistRole.entries.firstOrNull { it.name == myRole } ?: PlaylistRole.VIEWER,
        syncState = SharedSyncState.of(syncState),
        localPlaylistId = localPlaylistId
    )
}
