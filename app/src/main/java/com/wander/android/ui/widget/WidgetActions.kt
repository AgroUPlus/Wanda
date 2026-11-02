package com.wander.android.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.wander.android.core.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The widget's transport buttons.
 *
 * Each waits for the controller before acting. The widget is often tapped with the app process
 * freshly started — or not started at all — and calling into a controller that did not exist yet
 * dropped the tap. Play on an empty player reaches the session's playback resumption, which loads
 * the saved queue. The widget redraws from what the service then reports (see
 * [PlaybackWidgetUpdater]), so nothing here repaints it.
 */
private suspend fun Context.withPlayer(action: PlayerConnection.() -> Unit) {
    val connection = playerConnectionFor(this)
    if (!connection.awaitConnected(CONNECT_TIMEOUT_MS)) {
        Log.w(TAG, "The playback service did not answer the widget in time")
        return
    }
    // A MediaController may only be touched on the main thread.
    withContext(Dispatchers.Main.immediate) { connection.action() }
}

internal class TogglePlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.withPlayer { togglePlayPause() }
}

internal class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.withPlayer { next() }
}

internal class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.withPlayer { previous() }
}

private const val TAG = "NowPlayingWidget"

/** Long enough for a cold service start, short enough not to leave a tap hanging. */
private const val CONNECT_TIMEOUT_MS = 5_000L
