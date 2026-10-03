package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroSharedPlaylist
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.AgroStaleRevision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** How one sync ended. */
sealed interface SyncOutcome {
    /** Up to date. [dropped] edits made here no longer applied after someone else's change. */
    data class Synced(val dropped: Int) : SyncOutcome

    /** Gone, or no longer shared with this account. */
    data object Revoked : SyncOutcome

    /** Could not reach the server, or it refused for some other reason; the copy is marked behind. */
    data class Failed(val error: Throwable) : SyncOutcome
}

/**
 * Brings one shared playlist and the server into step: sends the edits made here, against the
 * revision the copy is at, then takes whatever the server answers as the copy.
 *
 * If someone else changed the playlist first, the server refuses the edits whole. They are then
 * replayed once against the fresh version — see [SharedPlaylistEdits.replay] — and anything that
 * no longer applies is dropped and counted rather than forced through. One sync at a time per
 * playlist, and local edits take the same lock, so the copy is never written from two sides at once.
 */
@Singleton
class SharedPlaylistSync @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val mirror: SharedPlaylistMirror,
    private val catchUp: SharedPlaylistCatchUp
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(agroId: String, block: suspend () -> T): T =
        locks.getOrPut(agroId) { Mutex() }.withLock { block() }

    suspend fun sync(agroId: String): SyncOutcome = withLock(agroId) {
        val entity = dao.get(agroId) ?: return@withLock SyncOutcome.Failed(IllegalStateException("No copy of $agroId"))
        if (!api.isAvailable) return@withLock behind(entity, IllegalStateException("Agro is not paired"))
        dao.setState(agroId, SharedSyncState.SYNCING.name)
        when {
            entity.revision == SharedPlaylistEntity.UNSYNCED && entity.localPlaylistId != null ->
                catchUp.run(entity, ::push, ::behind)
            dao.ops(agroId).isEmpty() -> pull(entity)
            else -> push(entity)
        }
    }

    private suspend fun pull(entity: SharedPlaylistEntity): SyncOutcome =
        api.fetch(entity.agroId).fold(
            onSuccess = { server ->
                write(entity, server, remaining = dao.ops(entity.agroId).map { SharedPlaylistOp.decode(it.opJson) })
                SyncOutcome.Synced(dropped = 0)
            },
            onFailure = { behind(entity, it) }
        )

    /** Sends every pending edit; on a stale revision, replays them once against the fresh copy. */
    internal suspend fun push(entity: SharedPlaylistEntity): SyncOutcome {
        val pending = dao.ops(entity.agroId)
        if (pending.isEmpty()) return pull(entity)
        val ops = pending.map { SharedPlaylistOp.decode(it.opJson) }
        val upTo = pending.last().seq

        val first = api.applyEdits(entity.agroId, entity.revision, ops.map { it.toEdit() })
        val stale = first.exceptionOrNull() as? AgroStaleRevision
        if (stale == null) {
            return first.fold(
                onSuccess = { server -> confirm(entity, server, ops, upTo, dropped = 0) },
                onFailure = { error -> if (error is AgroAuthError.Rejected) refused(entity, ops, upTo) else behind(entity, error) }
            )
        }

        val fresh = api.fetch(entity.agroId).getOrElse { return behind(entity, it) }
        val replayed = SharedPlaylistEdits.replay(ops, fresh.items.map { it.id })
        if (replayed.kept.isEmpty()) return confirm(entity, fresh, emptyList(), upTo, replayed.dropped)
        return api.applyEdits(entity.agroId, fresh.revision, replayed.kept.map { it.toEdit() }).fold(
            onSuccess = { server -> confirm(entity, server, replayed.kept, upTo, replayed.dropped) },
            onFailure = { error ->
                // Changed again in the moment between: show the fresh copy with the edits laid on
                // top, keep them queued, and let the next sync try once more.
                if (error is AgroStaleRevision) write(entity, fresh, ops, SharedSyncState.OUT_OF_SYNC)
                behind(entity, error)
            }
        )
    }

    /**
     * The server refused the edits outright: the owner turned collaboration off, or narrowed it,
     * since they were made. If the playlist can still be read, they will never be accepted, so
     * they are dropped and counted rather than retried forever, and the copy becomes what the
     * server holds. If it cannot, the copy is behind or revoked like any other failure.
     */
    private suspend fun refused(entity: SharedPlaylistEntity, ops: List<SharedPlaylistOp>, upTo: Long): SyncOutcome {
        val fresh = api.fetch(entity.agroId).getOrElse { return behind(entity, it) }
        return confirm(entity, fresh, emptyList(), upTo, dropped = ops.size)
    }

    private suspend fun confirm(
        entity: SharedPlaylistEntity,
        server: AgroSharedPlaylist,
        sent: List<SharedPlaylistOp>,
        upTo: Long,
        dropped: Int
    ): SyncOutcome {
        dao.clearOps(entity.agroId, upTo)
        val later = dao.ops(entity.agroId).map { SharedPlaylistOp.decode(it.opJson) }
        write(entity, server, later, if (later.isEmpty()) SharedSyncState.SYNCED else SharedSyncState.PENDING, sent.filterIsInstance<SharedPlaylistOp.Add>())
        return SyncOutcome.Synced(dropped)
    }

    private suspend fun write(
        entity: SharedPlaylistEntity,
        server: AgroSharedPlaylist,
        remaining: List<SharedPlaylistOp>,
        state: SharedSyncState = if (remaining.isEmpty()) SharedSyncState.SYNCED else SharedSyncState.PENDING,
        confirmed: List<SharedPlaylistOp.Add> = emptyList()
    ) = mirror.write(server, api.me, entity.localPlaylistId, state, confirmed = confirmed, remaining = remaining)

    /**
     * Marks the copy behind, or revoked when the server says this account may no longer open it.
     * Asked separately because "not found" and "forbidden" are deliberately indistinguishable in
     * the error itself, and neither may be confused with simply being offline.
     */
    internal suspend fun behind(entity: SharedPlaylistEntity, error: Throwable): SyncOutcome {
        val access = api.revisions(listOf(entity.agroId)).getOrNull()?.firstOrNull()
        return if (access != null && !access.accessible) {
            dao.setState(entity.agroId, SharedSyncState.REVOKED.name)
            SyncOutcome.Revoked
        } else {
            dao.setState(entity.agroId, SharedSyncState.OUT_OF_SYNC.name)
            SyncOutcome.Failed(error)
        }
    }
}
