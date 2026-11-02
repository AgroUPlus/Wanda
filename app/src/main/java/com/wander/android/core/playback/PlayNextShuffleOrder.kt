package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import java.util.Random

/**
 * Media3's random shuffle, except that "play next" means next.
 *
 * "Play next" inserts right after the playing item, which is next in the unshuffled order only.
 * [DefaultShuffleOrder] gives every inserted item a random place in the shuffled one, so with shuffle
 * on a track queued to play next could come up anywhere in the rest of the queue — or not before
 * the end of it. An insert at the slot straight after the playing item is therefore placed straight
 * after it in the shuffled order too, in the order it was given; any other insert stays random.
 *
 * Two more departures, both so the shuffled order means what the queue shows as "played" and "up
 * next": a new list starts the shuffle at its start item ([cloneAndSet]), and a move keeps each item
 * where it was in the shuffled order ([cloneAndMove]) rather than letting its slot pass to whatever
 * now sits at its old index.
 *
 * [currentIndex] is read only from [cloneAndInsert] on a non-empty order, which ExoPlayer calls on
 * the application thread before it applies the insert — so it is the playing item's index from
 * before the insert. The playback thread only ever clones an empty order, which never reads it.
 */
@OptIn(UnstableApi::class)
internal class PlayNextShuffleOrder private constructor(
    private val delegate: ShuffleOrder,
    private val currentIndex: () -> Int,
    private val random: Random
) : ShuffleOrder {

    constructor(currentIndex: () -> Int, random: Random = Random()) :
        this(DefaultShuffleOrder(0, random.nextLong()), currentIndex, random)

    override fun getLength(): Int = delegate.length
    override fun getNextIndex(index: Int): Int = delegate.getNextIndex(index)
    override fun getPreviousIndex(index: Int): Int = delegate.getPreviousIndex(index)
    override fun getLastIndex(): Int = delegate.lastIndex
    override fun getFirstIndex(): Int = delegate.firstIndex

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (delegate.length == 0 || insertionCount == 0) {
            return wrap(delegate.cloneAndInsert(insertionIndex, insertionCount))
        }
        val current = currentIndex()
        if (current !in 0 until delegate.length || insertionIndex != current + 1) {
            return wrap(delegate.cloneAndInsert(insertionIndex, insertionCount))
        }
        val shuffled = shuffledIndices()
            .map { if (it >= insertionIndex) it + insertionCount else it }
            .toMutableList()
        // `current` sits before the insertion point, so the shift above leaves it where it was.
        shuffled.addAll(shuffled.indexOf(current) + 1, (insertionIndex until insertionIndex + insertionCount).toList())
        return fromShuffled(shuffled)
    }

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        val length = delegate.length
        val unshuffled = (0 until length).toMutableList()
        val moved = unshuffled.subList(indexFrom, indexToExclusive).toList()
        unshuffled.subList(indexFrom, indexToExclusive).clear()
        unshuffled.addAll(newIndexFrom, moved)
        // unshuffled[newIndex] is the item's old index; invert that to follow each item to its new one.
        val newIndexOf = IntArray(length)
        unshuffled.forEachIndexed { newIndex, oldIndex -> newIndexOf[oldIndex] = newIndex }
        return fromShuffled(shuffledIndices().map { newIndexOf[it] })
    }

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder {
        val shuffled = delegate.cloneAndClear().cloneAndInsert(0, insertionCount).let(::orderOf).toMutableList()
        if (startIndex in 0 until insertionCount) {
            shuffled.remove(startIndex)
            shuffled.add(0, startIndex)
        }
        return fromShuffled(shuffled)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder =
        wrap(delegate.cloneAndRemove(indexFrom, indexToExclusive))

    override fun cloneAndClear(): ShuffleOrder = wrap(delegate.cloneAndClear())

    /** A fresh shuffle of the current length that starts at [startIndex]. */
    fun reshuffledFrom(startIndex: Int): ShuffleOrder = cloneAndSet(delegate.length, startIndex)

    private fun shuffledIndices(): List<Int> = orderOf(delegate)

    private fun fromShuffled(shuffled: List<Int>): ShuffleOrder =
        wrap(DefaultShuffleOrder(shuffled.toIntArray(), random.nextLong()))

    private fun wrap(order: ShuffleOrder) = PlayNextShuffleOrder(order, currentIndex, random)

    companion object {
        /**
         * Gives [player] this shuffle, and starts the shuffled order at the playing item each time
         * shuffle is switched on.
         *
         * Without the second half the order fixed when the list was set stays in force, and turning
         * shuffle on part-way through put the playing item somewhere in its middle: everything
         * before it counted as played without ever having been heard, and would not be.
         */
        fun install(player: ExoPlayer) {
            val initial = PlayNextShuffleOrder({ player.currentMediaItemIndex })
            player.setShuffleOrder(initial)
            player.addListener(object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    if (!shuffleModeEnabled) return
                    val order = player.shuffleOrder as? PlayNextShuffleOrder ?: return
                    player.setShuffleOrder(order.reshuffledFrom(player.currentMediaItemIndex))
                }
            })
        }

        private fun orderOf(order: ShuffleOrder): List<Int> {
            val out = ArrayList<Int>(order.length)
            var index = order.firstIndex
            while (index != C.INDEX_UNSET && out.size < order.length) {
                out += index
                index = order.getNextIndex(index)
            }
            return out
        }
    }
}
