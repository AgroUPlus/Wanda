package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.data.sources.agro.AgroPlaylistEdit
import com.wander.android.data.sources.agro.AgroPlaylistTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One edit made on this device to a shared playlist, kept until the server confirms it.
 *
 * Applied to the local copy the moment it is made, so the screen answers at once, and sent later
 * against the revision the copy was at. If someone else changed the playlist first, [replay] keeps
 * the edits that still make sense on top of the new version. The same rule as the dashboard's
 * `playlistReplay.js`, so the two never disagree about what happened.
 */
@Serializable
sealed interface SharedPlaylistOp {
    /** Appends a track. [pendingItemId] stands in for the server's id until the server assigns one. */
    @Serializable
    data class Add(
        val pendingItemId: String,
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String? = null,
        val durationMs: Long = 0L
    ) : SharedPlaylistOp

    @Serializable
    data class Remove(val itemId: String) : SharedPlaylistOp

    /** Places [itemId] after [afterItemId], or first when that is null. */
    @Serializable
    data class Move(val itemId: String, val afterItemId: String?) : SharedPlaylistOp

    fun toEdit(): AgroPlaylistEdit = when (this) {
        is Add -> AgroPlaylistEdit.Add(AgroPlaylistTrack(title, artist, album, durationMs))
        is Remove -> AgroPlaylistEdit.Remove(itemId)
        is Move -> AgroPlaylistEdit.Move(itemId, afterItemId)
    }

    companion object {
        private val json = Json { classDiscriminator = "op" }

        fun encode(op: SharedPlaylistOp): String = json.encodeToString(serializer(), op)

        fun decode(text: String): SharedPlaylistOp = json.decodeFromString(serializer(), text)
    }
}

/** What survived a replay, and how many edits no longer applied. */
data class Replayed(val kept: List<SharedPlaylistOp>, val dropped: Int)

internal object SharedPlaylistEdits {

    /**
     * The edits worth sending again once the playlist has become [fresh]. An add always is; a remove
     * or a move only while its item is still there, and a move only while its anchor is too.
     */
    fun replay(ops: List<SharedPlaylistOp>, fresh: List<String>): Replayed {
        val present = fresh.toSet()
        var dropped = 0
        val kept = ops.filter { op ->
            val keep = when (op) {
                is SharedPlaylistOp.Add -> true
                is SharedPlaylistOp.Remove -> op.itemId in present
                is SharedPlaylistOp.Move -> op.itemId in present && (op.afterItemId == null || op.afterItemId in present)
            }
            if (!keep) dropped++
            keep
        }
        return Replayed(kept, dropped)
    }

    /** [items] with [op] applied, renumbered. What the screen shows before the server answers. */
    fun applyLocally(items: List<SharedPlaylistItemEntity>, op: SharedPlaylistOp, me: String): List<SharedPlaylistItemEntity> {
        val next = items.toMutableList()
        when (op) {
            is SharedPlaylistOp.Add -> next += SharedPlaylistItemEntity(
                agroId = items.firstOrNull()?.agroId.orEmpty(),
                itemId = op.pendingItemId,
                position = next.size,
                trackId = op.trackId,
                title = op.title,
                artist = op.artist,
                album = op.album,
                durationMs = op.durationMs,
                addedBy = me
            )
            is SharedPlaylistOp.Remove -> next.removeAll { it.itemId == op.itemId }
            is SharedPlaylistOp.Move -> {
                val from = next.indexOfFirst { it.itemId == op.itemId }
                if (from >= 0) {
                    val item = next.removeAt(from)
                    val to = op.afterItemId?.let { anchor -> next.indexOfFirst { it.itemId == anchor } + 1 } ?: 0
                    next.add(to.coerceIn(0, next.size), item)
                }
            }
        }
        return next.mapIndexed { index, item -> item.copy(position = index) }
    }

    /**
     * A move expressed against the items the server knows: placed after the nearest earlier item
     * that is not still pending, since the server cannot be pointed at one it has not created.
     */
    fun anchorBefore(items: List<SharedPlaylistItemEntity>, targetIndex: Int, moving: String): String? =
        items.filter { it.itemId != moving }
            .take(targetIndex)
            .lastOrNull { !it.isPending }
            ?.itemId

    /**
     * What turns a copy published long ago into the Wanda playlist it came from: a remove for every
     * item the playlist no longer holds, and an add for every track it gained. Order is left to
     * [movesToMatch], which needs the server's ids for the added tracks first.
     */
    fun catchUpOps(items: List<SharedPlaylistItemEntity>, local: List<KnownTrack>, newId: () -> String): List<SharedPlaylistOp> {
        val unclaimed = local.map { it.trackId }.toMutableList()
        val removes = items.mapNotNull { item ->
            if (unclaimed.remove(item.trackId)) null else SharedPlaylistOp.Remove(item.itemId)
        }
        val adds = local.filter { unclaimed.remove(it.trackId) }.map {
            SharedPlaylistOp.Add(newId(), it.trackId, it.title, it.artist, it.album, it.durationMs)
        }
        return removes + adds
    }

    /** The fewest moves, front to back, that put [items] in the order of [desired] track ids. */
    fun movesToMatch(items: List<SharedPlaylistItemEntity>, desired: List<String>): List<SharedPlaylistOp.Move> {
        val current = items.toMutableList()
        val moves = mutableListOf<SharedPlaylistOp.Move>()
        var previous: String? = null
        desired.forEachIndexed { index, trackId ->
            if (index >= current.size) return moves
            val found = (index until current.size).firstOrNull { current[it].trackId == trackId } ?: return@forEachIndexed
            if (found != index) {
                val item = current.removeAt(found)
                current.add(index, item)
                moves += SharedPlaylistOp.Move(item.itemId, previous)
            }
            previous = current[index].itemId
        }
        return moves
    }
}
