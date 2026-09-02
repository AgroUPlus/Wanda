package com.wander.android.core.playback

import com.wander.android.data.model.isOneShotTrackId

/**
 * Whether the item after this one may be fetched ahead of time.
 *
 * Preloading is a small, unglamorous win: two seconds of the next track already decoded means a
 * skip starts instantly instead of after a round trip. It is also a request made for audio nobody
 * has asked to hear yet, so there are two kinds of item it must never touch.
 *
 * A pure function, and separate from the player for that reason. The consequence of getting it
 * wrong is a `409` on somebody's shared track, which is exactly the class of bug that took a whole
 * session to find the first time — it deserves a test rather than a code review.
 */
internal object PreloadDecision {

    /**
     * True when the next queued item can be fetched early.
     *
     * Takes the two facts rather than the `MediaItem` they come from, so the rule can be tested
     * without the Android framework — `Uri.parse` is a stub on the JVM and would have made this
     * untestable for no benefit.
     *
     * Refused for a **one-shot** stream: a relay session serves its receiving half once, so
     * preloading it consumes the transfer and the real playback is answered `409`. Refused for a
     * **livestream**: there is no "beginning" to fetch early, and the edge preloaded now will not
     * be the edge when the track starts.
     */
    fun canPreload(nextMediaId: String?, isLive: Boolean): Boolean {
        val id = nextMediaId ?: return false
        if (isLive) return false
        return id.isNotBlank() && !isOneShotTrackId(id)
    }
}
