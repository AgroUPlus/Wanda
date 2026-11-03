package com.wander.android.ui.screens.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.orderedIndices
import com.wander.android.data.model.UnifiedTrack

/**
 * The role of an entry within the playback timeline of the queue.
 */
internal enum class QueueItemRole {
    PREVIOUS,
    CURRENT,
    UP_NEXT
}

/**
 * One row of the queue: the track, where it sits in the player's timeline, its role,
 * and a key that survives reorders and updates.
 */
internal data class QueueEntry(
    val key: String,
    val track: UnifiedTrack,
    val queueIndex: Int,
    val role: QueueItemRole
)

/**
 * Prepares queue entries for the drawer preserving full history: previous tracks, the active
 * track, and upcoming tracks — in the order they play, which under shuffle is not the order the
 * player holds them in.
 */
@Composable
internal fun rememberQueueEntries(
    state: PlaybackState,
    generations: Map<String, Int> = emptyMap()
): List<QueueEntry> {
    val queue = state.queue
    val order = state.orderedIndices
    val currentIndex = state.currentIndex
    return remember(queue, order, currentIndex, generations) {
        queueEntries(queue, order, currentIndex, generations)
    }
}

/**
 * The entries of [queue] laid out in [order], each role read from where it falls relative to
 * [currentIndex] in that order.
 *
 * A key counts repeats of a track in the player's index order rather than in the play order, so
 * switching shuffle on or off moves rows instead of recreating them.
 */
internal fun queueEntries(
    queue: List<UnifiedTrack>,
    order: List<Int>,
    currentIndex: Int,
    generations: Map<String, Int>
): List<QueueEntry> {
    val seen = mutableMapOf<String, Int>()
    val keys = queue.map { track ->
        val occurrence = seen.merge(track.id, 1, Int::plus)!! - 1
        "${track.id}#$occurrence#g${generations[track.id] ?: 0}"
    }
    val currentPosition = order.indexOf(currentIndex)
    return order.mapIndexed { position, index ->
        val role = when {
            currentPosition < 0 -> QueueItemRole.UP_NEXT
            position < currentPosition -> QueueItemRole.PREVIOUS
            position == currentPosition -> QueueItemRole.CURRENT
            else -> QueueItemRole.UP_NEXT
        }
        QueueEntry(key = keys[index], track = queue[index], queueIndex = index, role = role)
    }
}
