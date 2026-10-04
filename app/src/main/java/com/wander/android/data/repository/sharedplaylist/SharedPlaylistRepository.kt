package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroPlaylistApi
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
    val localPlaylistId: String?,
    val isBlend: Boolean = false
) {
    val isCollaborative: Boolean get() = editAccess != EditAccess.OFF

    /**
     * What this account may do right now: nothing at all once it is no longer shared, and nothing
     * to a blend ever — Agro writes it, and refuses every hand edit, its creator's included.
     */
    val effectiveRole: PlaylistRole
        get() = if (syncState == SharedSyncState.REVOKED || isBlend) PlaylistRole.VIEWER else role
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
    private val agroPlaylists: AgroPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val mirror: SharedPlaylistMirror,
    private val blends: com.wander.android.data.sources.agro.AgroBlendApi
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

    /**
     * Brings the copies in line with what this account owns and follows on Agro, wherever that
     * happened — a playlist made in the dashboard, one followed there or on another device, and
     * one unfollowed elsewhere. A playlist that fails to load is left for the next look; either
     * list failing is the error, so being offline is not mistaken for owning or following nothing.
     */
    suspend fun discover(): Result<Unit> = runCatching {
        val owned = api.ownedIds().getOrThrow()
        val followed = api.followedIds().getOrThrow()
        (owned + followed.open).distinct().filter { dao.get(it) == null }.forEach { id -> keepOwn(id) }
        dao.followed()
            .filter { it.agroId !in followed.open && it.agroId !in followed.revoked && isFollowedCopy(it) }
            .forEach { forget(it.agroId) }
    }

    /**
     * A follow or unfollow made elsewhere, pushed by Agro: keep a copy of a playlist just followed,
     * or let go of one just unfollowed.
     */
    suspend fun followedElsewhere(agroId: String, following: Boolean) {
        val copy = dao.get(agroId)
        when {
            following && copy == null -> keepOwn(agroId)
            !following && copy != null && isFollowedCopy(copy) -> forget(agroId)
        }
    }

    /**
     * A copy kept only because this account follows it. Not its own, which unfollowing would not
     * remove, and not a blend, which is left rather than unfollowed.
     */
    private fun isFollowedCopy(copy: SharedPlaylistEntity): Boolean =
        copy.localPlaylistId == null && !copy.isBlend && copy.myRole != PlaylistRole.OWNER.name

    /** Keeps a copy of [agroId], owned or followed on Agro, answering the id to open it by. */
    suspend fun keepOwn(agroId: String): Result<String> = api.fetch(agroId).map {
        mirror.write(it, api.me, localPlaylistId = null, state = SharedSyncState.SYNCED)
        SharedPlaylistMirror.routeId(agroId)
    }

    /**
     * Takes [agroId] out of this account's library: deleted from the server when this account
     * owns it, since unfollowing your own playlist would leave it there; unfollowed otherwise.
     */
    suspend fun remove(agroId: String): Result<Unit> {
        // A blend is left rather than unfollowed or deleted: leaving takes this account's listening
        // out of it, and its creator leaving ends it — see `AgroBlendApi.leave`.
        // One already ended has nobody left to tell, so it is only forgotten.
        val copy = dao.get(agroId)
        if (copy?.isBlend == true && copy.syncState == SharedSyncState.REVOKED.name) return Result.success(forget(agroId))
        if (copy?.isBlend == true) return leaveBlend(agroId)
        if (dao.get(agroId)?.myRole != PlaylistRole.OWNER.name) return unfollow(agroId)
        return agroPlaylists.delete(agroId).onSuccess {
            dao.forget(agroId)
            trackDao.deleteUnreferencedUnresolved()
        }
    }

    /**
     * Leaves a blend. Refused because it has already ended — and the copy not yet told — it is
     * forgotten all the same: there is nothing left to leave. Any other failure, offline say,
     * stays an error, so a blend still running is never dropped here while still on the server.
     */
    private suspend fun leaveBlend(agroId: String): Result<Unit> {
        val left = blends.leave(agroId)
        if (left.isSuccess) return left.onSuccess { forget(agroId) }
        val gone = api.revisions(listOf(agroId)).getOrNull()?.firstOrNull()?.accessible == false
        return if (gone) Result.success(forget(agroId)) else left
    }

    /** Stops following and forgets the copy. Forgotten even when the server cannot be told. */
    suspend fun unfollow(agroId: String): Result<Unit> {
        val told = api.unfollow(agroId)
        forget(agroId)
        return told
    }

    /** Drops the copy of [agroId] and any placeholder tracks only it was holding. */
    suspend fun forget(agroId: String) {
        dao.forget(agroId)
        trackDao.deleteUnreferencedUnresolved()
    }

    /**
     * The shared state behind the playlist [playlistId] opens, whether followed or published.
     *
     * Only a Wanda playlist is tied to its copy. A backend's playlist published before it had to
     * be converted first is a snapshot on Agro: its screen shows the backend, and its edits go
     * there, so it has no shared state here — see [isTiedToCopy].
     */
    fun observe(playlistId: String): Flow<SharedPlaylistState?> {
        SharedPlaylistMirror.agroIdOf(playlistId)?.let { agroId -> return dao.observe(agroId).map { it?.toState() } }
        if (!isTiedToCopy(playlistId)) return flowOf(null)
        return dao.observeForLocal(playlistId).map { it?.toState() }
    }

    /** The items behind [playlistId] in order, empty while it is not shared. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeItems(playlistId: String): Flow<List<SharedPlaylistItemEntity>> =
        observe(playlistId).flatMapLatest { state ->
            state?.let { dao.observeItems(it.agroId) } ?: flowOf(emptyList())
        }

    suspend fun agroIdFor(playlistId: String): String? =
        SharedPlaylistMirror.agroIdOf(playlistId)
            ?: dao.forLocal(playlistId)?.takeIf { isTiedToCopy(playlistId) }?.agroId

    /**
     * Where an edit to [playlistId] goes, when that is the shared copy. A published playlist whose
     * copy has not caught up yet is edited as the Wanda playlist it still is: the catch-up sends
     * it whole, where an edit to the empty copy would be written back over it.
     */
    suspend fun editTarget(playlistId: String): String? {
        SharedPlaylistMirror.agroIdOf(playlistId)?.let { return it }
        if (!isTiedToCopy(playlistId)) return null
        val copy = dao.forLocal(playlistId) ?: return null
        return copy.agroId.takeIf { copy.revision != SharedPlaylistEntity.UNSYNCED }
    }

    /**
     * The copy's tracks in order, one per item. An item whose track row has gone plays as the
     * metadata the server holds, so the list never shifts under an edit that names a position.
     */
    suspend fun tracks(agroId: String): List<SharedTrack> {
        val items = dao.items(agroId)
        val byId = trackDao.getTracksByIdsChunked(items.map { it.trackId }).associateBy { it.id }
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

    /** Whether [playlistId] is a Wanda playlist, the only kind kept in step with its Agro copy. */
    private fun isTiedToCopy(playlistId: String): Boolean = playlistId.startsWith(SourceType.LOCAL.idPrefix)

    /**
     * The Agro playlists that are not a Wanda playlist's copy — followed ones, and this account's
     * own made outside Wanda — for the library and for the source that serves them.
     */
    suspend fun followed(): List<UnifiedPlaylist> = dao.followed().map { it.toPlaylist() }

    /** Followed playlists this account may add to right now: the add-to-playlist sheet's extra targets. */
    suspend fun writableFollowed(): List<UnifiedPlaylist> =
        dao.followed().filter { row -> row.toState().effectiveRole.canAdd }
            .map { it.toPlaylist() }

    suspend fun followedPlaylist(agroId: String): UnifiedPlaylist? = dao.get(agroId)?.toPlaylist()

    private suspend fun SharedPlaylistEntity.toPlaylist(): UnifiedPlaylist {
        val items = dao.items(agroId)
        // One read for every track's cover rather than one per track until a cover turns up: a
        // copy still being matched has no covers at all, and that walked the whole list.
        val covers = trackDao.getTracksByIdsChunked(items.map { it.trackId }).associate { it.id to it.artworkUrl }
        val cover = items.firstNotNullOfOrNull { covers[it.trackId]?.takeIf(String::isNotBlank) }
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
        localPlaylistId = localPlaylistId,
        isBlend = isBlend
    )
}
