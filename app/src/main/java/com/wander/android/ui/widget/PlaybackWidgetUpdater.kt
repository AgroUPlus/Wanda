package com.wander.android.ui.widget

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wander.android.R
import com.wander.android.core.playback.toUnifiedTrack
import com.wander.android.data.model.nameRes
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pushes what the player is doing to the Now Playing widget on the events that change what it
 * shows — a track change or a play/pause — rather than the widget polling. See [WidgetSnapshot].
 *
 * The snapshot is read on the player's thread, where the listener runs, and written off it. Writes
 * run on a scope of their own rather than the service's, so [onServiceStopped] can still record
 * "not playing" while the service is being torn down; otherwise a widget outlived by its process
 * would show a pause button for a player that is gone.
 */
internal class PlaybackWidgetUpdater(
    private val context: Context,
    private val player: Player
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
    override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

    /** Call from the service's `onDestroy`, before the player is released. */
    fun onServiceStopped() = push(snapshot().copy(isPlaying = false))

    private fun refresh() = push(snapshot())

    private fun snapshot(): WidgetSnapshot {
        val track = player.currentMediaItem?.toUnifiedTrack()
        return WidgetSnapshot(
            title = track?.title,
            subtitle = track?.let {
                context.getString(R.string.widget_track_line, it.artist, context.getString(it.source.nameRes))
            },
            artworkUrl = track?.artworkUrl,
            isPlaying = player.isPlaying
        )
    }

    private fun push(snapshot: WidgetSnapshot) {
        // Only the newest state matters; one still being written is superseded, not queued behind.
        pending?.cancel()
        pending = scope.launch {
            try {
                WidgetSnapshot.push(context, snapshot)
            } catch (e: IOException) {
                // The widget's state file could not be written; it keeps showing the last state.
                Log.w(TAG, "Could not update the Now Playing widget: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackWidgetUpdater"
    }
}
