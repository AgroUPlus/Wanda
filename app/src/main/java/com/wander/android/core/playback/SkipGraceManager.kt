package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.session.MediaController

/**
 * 2-second grace window to restore playback position when returning from an accidental skip.
 */
internal data class SkipGraceWindow(
    val fromIndex: Int,
    val fromPositionMs: Long,
    val toIndex: Int,
    val timestampSystemMs: Long
)

/**
 * Handles track skip grace windows, track stepping vs restart decisions.
 */
internal class SkipGraceManager {

    private var skipGraceWindow: SkipGraceWindow? = null

    fun restartsOnPrevious(ctrl: MediaController?): Boolean {
        val grace = skipGraceWindow
        val prevIndex = ctrl?.previousMediaItemIndex ?: C.INDEX_UNSET
        if (grace != null &&
            prevIndex != C.INDEX_UNSET &&
            grace.fromIndex == prevIndex &&
            System.currentTimeMillis() - grace.timestampSystemMs <= SKIP_GRACE_WINDOW_MS
        ) {
            return false
        }
        return (ctrl?.currentPosition ?: 0L) > RESTART_THRESHOLD_MS
    }

    fun seekToIndex(ctrl: MediaController, index: Int) {
        val grace = skipGraceWindow
        if (grace != null && grace.fromIndex == index && System.currentTimeMillis() - grace.timestampSystemMs <= SKIP_GRACE_WINDOW_MS) {
            skipGraceWindow = null
            ctrl.seekTo(index, grace.fromPositionMs)
        } else {
            ctrl.seekToDefaultPosition(index)
        }
    }

    fun next(ctrl: MediaController) {
        val currentIndex = ctrl.currentMediaItemIndex
        val currentPos = ctrl.currentPosition
        val nextIndex = ctrl.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return

        val grace = skipGraceWindow
        if (grace != null && grace.fromIndex == nextIndex && System.currentTimeMillis() - grace.timestampSystemMs <= SKIP_GRACE_WINDOW_MS) {
            skipGraceWindow = null
            ctrl.seekTo(nextIndex, grace.fromPositionMs)
            return
        }

        skipGraceWindow = if (currentPos > 1000L) {
            SkipGraceWindow(
                fromIndex = currentIndex,
                fromPositionMs = currentPos,
                toIndex = nextIndex,
                timestampSystemMs = System.currentTimeMillis()
            )
        } else {
            null
        }

        ctrl.seekToNextMediaItem()
    }

    fun previous(ctrl: MediaController) {
        val prevIndex = ctrl.previousMediaItemIndex
        val grace = skipGraceWindow
        if (grace != null &&
            prevIndex != C.INDEX_UNSET &&
            grace.fromIndex == prevIndex &&
            System.currentTimeMillis() - grace.timestampSystemMs <= SKIP_GRACE_WINDOW_MS
        ) {
            skipGraceWindow = null
            ctrl.seekTo(prevIndex, grace.fromPositionMs)
            return
        }

        if (restartsOnPrevious(ctrl)) {
            ctrl.seekTo(0L)
        } else {
            recordSkipAndStepBack(ctrl)
        }
    }

    fun previousTrack(ctrl: MediaController) {
        val prevIndex = ctrl.previousMediaItemIndex
        val grace = skipGraceWindow
        if (grace != null &&
            prevIndex != C.INDEX_UNSET &&
            grace.fromIndex == prevIndex &&
            System.currentTimeMillis() - grace.timestampSystemMs <= SKIP_GRACE_WINDOW_MS
        ) {
            skipGraceWindow = null
            ctrl.seekTo(prevIndex, grace.fromPositionMs)
            return
        }

        recordSkipAndStepBack(ctrl)
    }

    private fun recordSkipAndStepBack(ctrl: MediaController) {
        val currentIndex = ctrl.currentMediaItemIndex
        val currentPos = ctrl.currentPosition
        val prevIndex = ctrl.previousMediaItemIndex
        if (prevIndex == C.INDEX_UNSET) return

        skipGraceWindow = if (currentPos > 1000L) {
            SkipGraceWindow(
                fromIndex = currentIndex,
                fromPositionMs = currentPos,
                toIndex = prevIndex,
                timestampSystemMs = System.currentTimeMillis()
            )
        } else {
            null
        }

        ctrl.seekToPreviousMediaItem()
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val SKIP_GRACE_WINDOW_MS = 2_000L
    }
}
