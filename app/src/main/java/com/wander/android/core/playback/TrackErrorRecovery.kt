package com.wander.android.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import com.wander.android.data.model.isOneShotTrackId

/**
 * What to do when one track fails, so that a single bad item does not stop the whole queue.
 *
 * An error used to leave the player stopped on the failed track with a snackbar, which for an
 * unattended queue — a car, a run, a sleep timer — meant silence until someone came back to it.
 * Two recoveries now come first:
 *
 * - **An expired stream URL is fetched again.** A YouTube URL carries an expiry, so a track paused
 *   long enough is refused with a 403 or 410 on resume. Re-preparing re-runs [StreamResolver], which
 *   mints a fresh URL, and playback carries on from the same position. Once per item: a second
 *   refusal means it was not the expiry.
 * - **A failed track is skipped.** But only a few in a row: when the network is down every track
 *   fails, and skipping would run through the whole queue in seconds and leave it at the end. A
 *   failure that is plainly the network does not skip at all, and neither does a livestream —
 *   advancing past one on a load error is what made streams look like they were being skipped —
 *   or a queue whose order belongs to someone else.
 *
 * The skip count resets as soon as a track actually gets going ([onItemReady]); the refresh is per
 * item ([onItemChanged]).
 */
internal class TrackErrorRecovery(private val maxConsecutiveSkips: Int = MAX_CONSECUTIVE_SKIPS) {

    /** The playing item's id once its stream has been fetched again; cleared when the item changes. */
    private var refreshedId: String? = null
    private var consecutiveSkips = 0

    /** Fetches an expired stream again and resumes where it failed. True when it did. */
    fun refreshExpiredStream(ctrl: MediaController, error: PlaybackException): Boolean {
        if (!PlaybackErrorTranslator.isExpiredStream(error)) return false
        val id = ctrl.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return false
        // A relay transfer is served exactly once; there is nothing to fetch again.
        if (isOneShotTrackId(id)) return false
        if (refreshedId == id) return false
        refreshedId = id

        ctrl.seekTo(ctrl.currentMediaItemIndex, ctrl.currentPosition.coerceAtLeast(0L))
        ctrl.prepare()
        return true
    }

    /**
     * Moves on to the next track after the playing one failed for good. True when it did. The error
     * is still reported by the caller either way, so the listener knows why a track went by.
     */
    fun skipPastFailedItem(
        ctrl: MediaController,
        error: PlaybackException,
        isLive: Boolean,
        orderLocked: Boolean
    ): Boolean {
        if (isLive || orderLocked) return false
        if (PlaybackErrorTranslator.isConnectivityFailure(error)) return false
        if (consecutiveSkips >= maxConsecutiveSkips) return false
        if (!ctrl.hasNextMediaItem()) return false

        consecutiveSkips++
        ctrl.seekToNextMediaItem()
        ctrl.prepare()
        return true
    }

    /** A track reached the point of playing: the failures skipped to get here no longer count. */
    fun onItemReady() {
        consecutiveSkips = 0
    }

    /** Another item is playing, and gets its own one refresh. */
    fun onItemChanged() {
        refreshedId = null
    }

    private companion object {
        /** Enough to get past a few dead tracks; few enough that an outage stops near where it began. */
        const val MAX_CONSECUTIVE_SKIPS = 3
    }
}
