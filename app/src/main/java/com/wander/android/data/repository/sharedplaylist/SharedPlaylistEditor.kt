package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.core.database.entity.SharedPlaylistOpEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.PlaylistRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Why an edit was not made. */
enum class EditRefusal { NOT_ALLOWED, PENDING_ITEM, NO_SUCH_ITEM }

/**
 * Edits made on this device to a shared playlist: applied to the copy at once, so the screen
 * answers immediately, and queued to send — see [SharedPlaylistRunner.syncSoon]. Works offline;
 * the queue goes out on the next sync.
 *
 * Permissions are checked here as well as on the server, so the screen never shows an edit it
 * would then have to take back. The server's answer is the one that counts.
 */
@Singleton
class SharedPlaylistEditor @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val sync: SharedPlaylistSync,
    private val mirror: SharedPlaylistMirror,
    private val runner: SharedPlaylistRunner
) {
    /** Appends [tracks], skipping any already in the playlist, as a Wanda playlist would. */
    suspend fun add(agroId: String, tracks: List<UnifiedTrack>): EditRefusal? = edit(agroId) { copy, items ->
        if (!role(copy).canAdd) return@edit Edit.Refused(EditRefusal.NOT_ALLOWED)
        val present = items.map { it.trackId }.toSet()
        val fresh = tracks.filter { it.id !in present }.distinctBy { it.id }
        // The copy points at these by id, so they need rows of their own, as for any playlist.
        trackDao.upsertTracks(fresh.map { TrackEntity.fromUnifiedTrack(it) })
        Edit.Ops(
            fresh.map {
                SharedPlaylistOp.Add(pendingId(), it.id, it.title, it.artist, it.album, it.durationMs)
            }
        )
    }

    /** Removes the track at [index]: any track for an owner or editor, one's own for a contributor. */
    suspend fun remove(agroId: String, index: Int): EditRefusal? = edit(agroId) { copy, items ->
        val item = items.getOrNull(index) ?: return@edit Edit.Refused(EditRefusal.NO_SUCH_ITEM)
        if (!canRemove(role(copy), item)) return@edit Edit.Refused(EditRefusal.NOT_ALLOWED)
        if (item.isPending) {
            // Never sent, so there is nothing to undo on the server: the add is simply withdrawn.
            withdraw(agroId, item.itemId)
            Edit.Ops(emptyList(), items.filterNot { it.itemId == item.itemId })
        } else {
            Edit.Ops(listOf(SharedPlaylistOp.Remove(item.itemId)))
        }
    }

    /** Moves the track at [from] to [to]. Owners and editors only. */
    suspend fun move(agroId: String, from: Int, to: Int): EditRefusal? = edit(agroId) { copy, items ->
        if (!role(copy).canRearrange) return@edit Edit.Refused(EditRefusal.NOT_ALLOWED)
        val item = items.getOrNull(from) ?: return@edit Edit.Refused(EditRefusal.NO_SUCH_ITEM)
        // The server cannot be told where to put an item it has not created yet.
        if (item.isPending) return@edit Edit.Refused(EditRefusal.PENDING_ITEM)
        Edit.Ops(listOf(SharedPlaylistOp.Move(item.itemId, SharedPlaylistEdits.anchorBefore(items, to, item.itemId))))
    }

    fun canRemove(role: PlaylistRole, item: SharedPlaylistItemEntity): Boolean =
        role.canRearrange || (role == PlaylistRole.CONTRIBUTOR && (item.addedBy.equals(api.me, ignoreCase = true) || item.isPending))

    private sealed interface Edit {
        data class Refused(val why: EditRefusal) : Edit
        /** [ops] to queue, and the items to show when they are not simply [ops] applied. */
        data class Ops(val ops: List<SharedPlaylistOp>, val shown: List<SharedPlaylistItemEntity>? = null) : Edit
    }

    private suspend fun edit(
        agroId: String,
        decide: suspend (SharedPlaylistEntity, List<SharedPlaylistItemEntity>) -> Edit
    ): EditRefusal? {
        val result = sync.withLock(agroId) {
            val copy = dao.get(agroId) ?: return@withLock Edit.Refused(EditRefusal.NO_SUCH_ITEM)
            if (copy.syncState == SharedSyncState.REVOKED.name) return@withLock Edit.Refused(EditRefusal.NOT_ALLOWED)
            val items = dao.items(agroId)
            val edit = decide(copy, items)
            if (edit is Edit.Ops) {
                val shown = edit.shown ?: edit.ops.fold(items) { acc, op -> SharedPlaylistEdits.applyLocally(acc, op, api.me) }
                    .map { it.copy(agroId = agroId) }
                dao.applyLocalEdit(agroId, shown, edit.ops.map { SharedPlaylistOpEntity(agroId = agroId, opJson = SharedPlaylistOp.encode(it)) })
                dao.setState(agroId, SharedSyncState.PENDING.name)
                copy.localPlaylistId?.let { mirror.reflectInto(it, copy.title, shown) }
            }
            edit
        }
        return when (result) {
            is Edit.Refused -> result.why
            is Edit.Ops -> {
                runner.syncSoon(agroId)
                null
            }
        }
    }

    private suspend fun withdraw(agroId: String, pendingItemId: String) {
        dao.ops(agroId).firstOrNull { row ->
            (SharedPlaylistOp.decode(row.opJson) as? SharedPlaylistOp.Add)?.pendingItemId == pendingItemId
        }?.let { dao.deleteOp(it.seq) }
    }

    /** A blend is Agro's to write, so nobody edits one by hand — see `refuse_generated` there. */
    private fun role(copy: SharedPlaylistEntity) =
        if (copy.isBlend) PlaylistRole.VIEWER
        else PlaylistRole.entries.firstOrNull { it.name == copy.myRole } ?: PlaylistRole.VIEWER

    private fun pendingId() = SharedPlaylistItemEntity.PENDING_PREFIX + UUID.randomUUID()
}
