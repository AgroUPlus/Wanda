package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.core.database.entity.SharedPlaylistOpEntity
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The first sync of a playlist published before copies were kept in step.
 *
 * Its copy on the server is whatever it held when it was shared, and the Wanda playlist has been
 * edited since — the very thing that never reached anyone. This sends those edits: the server's
 * items are matched to the Wanda playlist's tracks, what it lost is removed, what it gained is
 * added, and then the order is made to match. After this the playlist syncs like any other.
 */
@Singleton
class SharedPlaylistCatchUp @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val mirror: SharedPlaylistMirror
) {
    suspend fun run(
        entity: SharedPlaylistEntity,
        push: suspend (SharedPlaylistEntity) -> SyncOutcome,
        behind: suspend (SharedPlaylistEntity, Throwable) -> SyncOutcome
    ): SyncOutcome {
        val server = api.fetch(entity.agroId).getOrElse { return behind(entity, it) }
        val local = entity.localPlaylistId?.let { playlistDao.getPlaylistById(it) }
        if (local == null) {
            // No Wanda playlist to catch up from: it was deleted, or this was a backend's playlist
            // shared as a snapshot. Keep the server's copy as it is, still linked, so it can be
            // re-sent or unshared from where it was shared.
            mirror.write(server, api.me, entity.localPlaylistId, SharedSyncState.SYNCED)
            return SyncOutcome.Synced(dropped = 0)
        }

        // Captured before anything is written: the order being sent is the one the user left.
        val order = local.trackIds.split(',').filter { it.isNotBlank() }
        val byId = trackDao.getTracksByIdsChunked(order).associateBy { it.id }
        val known = order.mapNotNull { id ->
            byId[id]?.let { KnownTrack(it.id, it.title, it.artist, it.album, it.durationMs) }
        }
        mirror.write(server, api.me, entity.localPlaylistId, SharedSyncState.PENDING, known = known, reflect = false)

        val contents = SharedPlaylistEdits.catchUpOps(dao.items(entity.agroId), known, ::pendingId)
        if (contents.isNotEmpty()) {
            enqueue(entity.agroId, contents)
            val sent = push(dao.get(entity.agroId) ?: return SyncOutcome.Revoked)
            if (sent !is SyncOutcome.Synced) return sent
        }

        val moves = SharedPlaylistEdits.movesToMatch(dao.items(entity.agroId), order)
        if (moves.isEmpty()) {
            dao.get(entity.agroId)?.let { mirror.reflectInto(local.id, it.title, dao.items(entity.agroId)) }
            dao.setState(entity.agroId, SharedSyncState.SYNCED.name)
            return SyncOutcome.Synced(dropped = 0)
        }
        enqueue(entity.agroId, moves)
        return push(dao.get(entity.agroId) ?: return SyncOutcome.Revoked)
    }

    private suspend fun enqueue(agroId: String, ops: List<SharedPlaylistOp>) {
        var items: List<SharedPlaylistItemEntity> = dao.items(agroId)
        ops.forEach { items = SharedPlaylistEdits.applyLocally(items, it, api.me) }
        dao.applyLocalEdit(agroId, items, ops.map { SharedPlaylistOpEntity(agroId = agroId, opJson = SharedPlaylistOp.encode(it)) })
    }

    private fun pendingId() = SharedPlaylistItemEntity.PENDING_PREFIX + UUID.randomUUID()
}
