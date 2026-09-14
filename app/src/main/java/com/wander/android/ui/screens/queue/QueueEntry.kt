package com.wander.android.ui.screens.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Prepares queue entries for the drawer preserving full history:
 * previous tracks, the active track, and upcoming tracks.
 */
@Composable
internal fun rememberQueueEntries(
    queue: List<UnifiedTrack>,
    currentIndex: Int,
    generations: Map<String, Int> = emptyMap()
): List<QueueEntry> =
    remember(queue, currentIndex, generations) {
        val seen = mutableMapOf<String, Int>()
        queue.mapIndexed { index, track ->
            val occurrence = seen.merge(track.id, 1, Int::plus)!! - 1
            val gen = generations[track.id] ?: 0
            val role = when {
                index < currentIndex -> QueueItemRole.PREVIOUS
                index == currentIndex -> QueueItemRole.CURRENT
                else -> QueueItemRole.UP_NEXT
            }
            QueueEntry(
                key = "${track.id}#${occurrence}#g$gen",
                track = track,
                queueIndex = index,
                role = role
            )
        }
    }
