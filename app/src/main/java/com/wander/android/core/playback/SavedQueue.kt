package com.wander.android.core.playback

import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isOneShotTrackId
import kotlinx.serialization.Serializable

/**
 * The queue as it stood when it was last saved, so playback survives the process being killed.
 *
 * Whole tracks rather than ids: a queue holds search results, episodes and stations that were
 * never written to the library, so ids alone could not be turned back into anything playable.
 * Shuffle and repeat are not here — they are standing preferences, kept with the others.
 */
@Serializable
internal data class SavedQueue(
    val tracks: List<UnifiedTrack>,
    val index: Int,
    val positionMs: Long
) {
    companion object {
        /**
         * What of [tracks] may be saved, with [index] and [positionMs] moved to match.
         *
         * A one-shot track (a friend's relayed file — see [isOneShotTrackId]) names a transfer that
         * is consumed the first time it is read, so it is left out; so is a row with no identity of
         * its own. If the playing item is one of those, the queue resumes at the next track that
         * was kept, from its start. Null when nothing is left to save.
         */
        fun of(tracks: List<UnifiedTrack>, index: Int, positionMs: Long): SavedQueue? {
            val kept = ArrayList<UnifiedTrack>(tracks.size)
            var keptIndex = -1
            var keptBefore = 0
            tracks.forEachIndexed { i, track ->
                if (!isSavable(track)) return@forEachIndexed
                if (i == index) keptIndex = kept.size
                if (i < index) keptBefore++
                kept += track
            }
            if (kept.isEmpty()) return null
            if (keptIndex >= 0) {
                // A livestream has no position worth returning to: it rejoins at the live edge.
                val position = if (kept[keptIndex].isLive) 0L else positionMs.coerceAtLeast(0L)
                return SavedQueue(kept, keptIndex, position)
            }
            return SavedQueue(kept, keptBefore.coerceAtMost(kept.lastIndex), 0L)
        }

        private fun isSavable(track: UnifiedTrack): Boolean =
            !isOneShotTrackId(track.id) && !isUnidentifiedItemId(track.id)
    }
}
