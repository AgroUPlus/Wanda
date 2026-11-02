package com.wander.android.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Keeps the queue across process death, and hands it back when something asks to play again.
 *
 * Saving follows the player: a changed queue, a new item, a seek and a pause each schedule one
 * debounced write, and [flush] writes the last state when the service goes away. Position is not
 * written while a track simply plays on — that would be a disk write every few seconds for the
 * life of the queue — so a crash mid-track resumes from the last pause, seek or track change.
 *
 * Two ways back in. [restoreIfEmpty] fills an empty player when a controller first connects, so
 * the app, the widget and the notification open on what was playing — loaded, not prepared, so
 * nothing streams until play is pressed. [resumption] answers Media3's playback resumption, which
 * is what a Bluetooth play button or a widget tap reaches once the app has been killed.
 */
@UnstableApi
internal class QueueResumption(
    private val player: Player,
    private val store: SavedQueueStore,
    private val scope: CoroutineScope
) : Player.Listener {

    private var restoreAttempted = false

    /**
     * False until the saved queue has been read back or the player has been given a queue of its
     * own. Until then an empty player means "not restored yet", not "cleared", and saving it would
     * erase the very queue about to be restored.
     */
    private var settled = false
    private var pendingSave: Job? = null
    /** Decoded tracks by item instance: decoding is a JSON parse per item, and most saves change one. */
    private val decoded = IdentityHashMap<MediaItem, UnifiedTrack>()

    override fun onEvents(player: Player, events: Player.Events) {
        if (player.mediaItemCount > 0) settled = true
        val queueMoved = events.containsAny(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY
        )
        val paused = events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying
        if (queueMoved || paused) scheduleSave()
    }

    /** Writes the current state now. For the service's `onDestroy`, before the player is released. */
    fun flush() {
        pendingSave?.cancel()
        if (settled) store.save(snapshot())
    }

    /** Loads the saved queue into the player if it is still empty. Once per service. */
    fun restoreIfEmpty() {
        if (restoreAttempted) return
        restoreAttempted = true
        scope.launch {
            val saved = store.load()
            settled = true
            if (saved == null || player.mediaItemCount > 0) return@launch
            player.setMediaItems(saved.tracks.map(UnifiedTrack::toMediaItem), saved.index, saved.positionMs)
        }
    }

    /**
     * The saved queue for Media3 to resume with. When [isForPlayback] is false the caller only
     * wants to describe what would play, so only the current item is returned. Fails when nothing
     * was saved, which Media3 treats as "nothing to resume".
     */
    fun resumption(isForPlayback: Boolean): ListenableFuture<MediaItemsWithStartPosition> {
        restoreAttempted = true
        val result = SettableFuture.create<MediaItemsWithStartPosition>()
        val job = scope.launch {
            val saved = store.load()
            settled = true
            if (saved == null) {
                result.setException(NoSuchElementException("No saved queue to resume"))
                return@launch
            }
            val items: List<MediaItem> = saved.tracks.map(UnifiedTrack::toMediaItem)
            result.set(
                if (isForPlayback) {
                    MediaItemsWithStartPosition(items, saved.index, saved.positionMs)
                } else {
                    MediaItemsWithStartPosition(listOf(items[saved.index]), 0, saved.positionMs)
                }
            )
        }
        job.invokeOnCompletion { cause -> if (cause != null) result.cancel(false) }
        return result
    }

    private fun scheduleSave() {
        if (!settled) return
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.save(snapshot())
        }
    }

    private fun snapshot(): SavedQueue? {
        val count = player.mediaItemCount
        if (count == 0) {
            decoded.clear()
            return null
        }
        val items = (0 until count).map(player::getMediaItemAt)
        val tracks = items.mapIndexed { index, item ->
            decoded.getOrPut(item) { item.toUnifiedTrack() ?: item.toUnidentifiedTrack(index) }
        }
        // A cache of what is queued, not of everything ever queued.
        val queued = Collections.newSetFromMap(IdentityHashMap<MediaItem, Boolean>()).apply { addAll(items) }
        decoded.keys.retainAll(queued)
        return SavedQueue.of(tracks, player.currentMediaItemIndex, player.currentPosition)
    }

    private companion object {
        /** Long enough to fold a burst — a whole album queued item by item — into one write. */
        const val SAVE_DEBOUNCE_MS = 1_500L
    }
}
