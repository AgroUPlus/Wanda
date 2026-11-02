package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EpisodeProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Saves where the listener got to in an episode, from the service's own player.
 *
 * It used to be saved by the UI's controller, so an episode heard from a car, a watch or the
 * notification with the app never opened kept no position. The player is the one place every one
 * of those reaches, and it is the only writer: nothing on the UI side saves progress any more.
 *
 * Saved on pause and on leaving an episode — never while it simply plays on, which would be a
 * write every few seconds for an hour.
 */
internal class EpisodeProgressRecorder(
    private val player: Player,
    private val progress: EpisodeProgressRepository
) : Player.Listener {

    private val tracker = EpisodeCheckpointTracker()

    /**
     * Not the service's scope, which is cancelled in `onDestroy`: the pause that precedes the
     * service stopping is exactly the write that must land. Each write is one short upsert.
     */
    private val writes = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The last item decoded, since decoding is a JSON parse and `onEvents` fires often. */
    private var decodedItem: MediaItem? = null
    private var decodedTrack: UnifiedTrack? = null

    override fun onEvents(player: Player, events: Player.Events) {
        val current = track(player.currentMediaItem)
        tracker.rememberDuration(current, player.duration)
        // Not in a batch that also changed the playing item: `currentPosition` can still be the
        // old item's then. The old item was checkpointed by `onPositionDiscontinuity`.
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying && !events.changesPlayingItem()) {
            save(tracker.checkpoint(current, player.currentPosition, player.duration))
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val sameItemSeek = reason == Player.DISCONTINUITY_REASON_SEEK &&
            oldPosition.mediaItemIndex == newPosition.mediaItemIndex
        if (sameItemSeek) return
        save(tracker.checkpoint(track(oldPosition.mediaItem), oldPosition.positionMs, C.TIME_UNSET))
    }

    /** Saves the playing episode's position now. For the service's `onDestroy`, before release. */
    fun flush() {
        if (!player.isPlaying) return
        save(tracker.checkpoint(track(player.currentMediaItem), player.currentPosition, player.duration))
    }

    private fun save(checkpoint: EpisodeCheckpoint?) {
        checkpoint ?: return
        writes.launch { progress.save(checkpoint.trackId, checkpoint.positionMs, checkpoint.durationMs) }
    }

    private fun track(item: MediaItem?): UnifiedTrack? {
        if (item == null) return null
        if (item !== decodedItem) {
            decodedItem = item
            decodedTrack = item.toUnifiedTrack()
        }
        return decodedTrack
    }
}
