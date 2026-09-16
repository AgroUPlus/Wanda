package com.wander.android.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Refreshes the Now Playing widget on the events that change what it shows — a track change
 * or a play/pause — rather than the widget polling. `updateAll` is cheap to call more often
 * than needed (Glance no-ops an unchanged widget), so this errs toward calling it too eagerly.
 */
internal class PlaybackWidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope
) : Player.Listener {

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
    override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

    private fun refresh() {
        scope.launch {
            runCatching {
                NowPlayingWidget().updateAll(context)
            }
        }
    }
}
