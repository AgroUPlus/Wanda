package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

// The queue in the order it will actually play.
//
// `PlaybackState.queue` is in the player's index order, which is the play order only while shuffle
// is off. Everything that reads "what came before" or "what comes next" — the queue's played and up
// next sections, the covers either side of the playing one — reads it through here instead.

/** [PlaybackState.playOrder], or the plain index order while it does not cover the whole queue. */
internal val PlaybackState.orderedIndices: List<Int>
    get() = if (playOrder.size == queue.size) playOrder else queue.indices.toList()

/** Where the playing item sits in [orderedIndices], or -1 when nothing is playing. */
internal val PlaybackState.playPosition: Int
    get() = orderedIndices.indexOf(currentIndex)

/**
 * The queue index [offset] places from the playing item in play order — -1 is the one before it,
 * 1 the one after — or null when there is none.
 */
internal fun PlaybackState.indexInPlayOrder(offset: Int): Int? {
    val order = orderedIndices
    val position = order.indexOf(currentIndex)
    if (position < 0) return null
    return order.getOrNull(position + offset)
}

/**
 * The window indices of [timeline] in play order, ignoring repeat: a repeat-all queue still has a
 * first and a last item, and the queue screen draws it that way.
 */
internal fun Timeline.playOrder(shuffle: Boolean): List<Int> {
    if (isEmpty) return emptyList()
    val out = ArrayList<Int>(windowCount)
    var index = getFirstWindowIndex(shuffle)
    while (index != C.INDEX_UNSET && out.size < windowCount) {
        out += index
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
    }
    return out
}
