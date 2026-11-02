package com.wander.android.core.playback

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Track gains a *stream resolve* learned, for [LoudnessNormaliser] to use where the file has no
 * ReplayGain or R128 tags and the source reported none in the track's extras.
 *
 * YouTube Music only says how loud a track is in its `/player` response, which is fetched when the
 * loader opens the stream — long after the `MediaItem` and its extras were built — so the value
 * cannot travel in the item. [StreamResolver] records it here instead, on ExoPlayer's loading
 * thread; the normaliser reads it on the main thread once the track's formats are known, which is
 * always after that resolve.
 *
 * Every resolve overwrites the track's entry, a stream with no value included, so a hint never
 * outlives the stream it described. Bounded, as only the queue's tracks are ever asked about.
 */
@Singleton
class LoudnessHints @Inject constructor() {

    private val gains = ConcurrentHashMap<String, Float>()

    internal fun record(trackId: String, trackGainDb: Float?) {
        if (trackGainDb == null) {
            gains.remove(trackId)
            return
        }
        if (gains.size >= MAX_ENTRIES && trackId !in gains) gains.clear()
        gains[trackId] = trackGainDb
    }

    /** The resolved stream's gain as [ReplayGainInfo], or [ReplayGainInfo.NONE]'s equal. */
    internal fun forTrack(trackId: String): ReplayGainInfo =
        ReplayGainInfo(trackGainDb = gains[trackId], albumGainDb = null)

    private companion object {
        /** Far beyond any queue's lookahead; clearing past it only costs re-learning on next resolve. */
        const val MAX_ENTRIES = 512
    }
}
