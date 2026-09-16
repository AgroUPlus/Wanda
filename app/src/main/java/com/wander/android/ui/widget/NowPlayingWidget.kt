package com.wander.android.ui.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wander.android.R

/**
 * The 4×2 Now Playing widget: title, artist, source, and the same transport the docked strip
 * offers. Live updates come from `PlaybackService`'s `WidgetUpdater` listener, which calls
 * [androidx.glance.appwidget.GlanceAppWidget.updateAll] on the events that matter rather than this
 * widget polling anything.
 *
 * [GlanceTheme] is what gives this Material You dynamic tinting on API 31+ with a static fallback
 * below it — the same tradeoff [com.wander.android.ui.components.player.FluidAmbientShader] makes
 * for the AGSL shader elsewhere in this suite, just for a widget host instead of an OS version.
 *
 * No artwork: rendering a remote `artworkUrl` needs a bitmap decoded through Coil *outside*
 * Compose — Glance is RemoteViews underneath, so it cannot load a network image the way a normal
 * composable can — which is a second image pipeline this change did not want to introduce
 * alongside everything else in this suite. Left for a follow-up rather than guessed at.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val playerConnection = playerConnectionFor(context)
        playerConnection.connect()
        val state = playerConnection.state.value

        // Read once here, not with `stringResource` inside the content below: Glance's
        // `provideContent` composables don't run in a normal Compose/resources context the way an
        // Activity's do, so this is the one place `context.getString` is guaranteed to work.
        val trackLine = state.currentTrack?.let { track ->
            context.getString(R.string.widget_track_line, track.artist, track.source.displayName)
        }
        val nothingPlaying = context.getString(R.string.widget_nothing_playing)
        val previousLabel = context.getString(R.string.widget_previous)
        val nextLabel = context.getString(R.string.widget_next)
        val playPauseLabel = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(12.dp)
                ) {
                    val track = state.currentTrack
                    if (track == null) {
                        Text(nothingPlaying, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        Text(track.title, style = TextStyle(color = GlanceTheme.colors.onSurface))
                        trackLine?.let {
                            Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                        }
                    }

                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = previousLabel,
                            style = TextStyle(color = GlanceTheme.colors.primary),
                            modifier = GlanceModifier.clickable(actionRunCallback<PreviousAction>())
                        )
                        Text(
                            text = playPauseLabel,
                            style = TextStyle(color = GlanceTheme.colors.primary),
                            modifier = GlanceModifier
                                .padding(horizontal = 16.dp)
                                .clickable(actionRunCallback<TogglePlayPauseAction>())
                        )
                        Text(
                            text = nextLabel,
                            style = TextStyle(color = GlanceTheme.colors.primary),
                            modifier = GlanceModifier.clickable(actionRunCallback<NextAction>())
                        )
                    }
                }
            }
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

internal class TogglePlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        playerConnectionFor(context).togglePlayPause()
        NowPlayingWidget().update(context, glanceId)
    }
}

internal class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        playerConnectionFor(context).next()
        NowPlayingWidget().update(context, glanceId)
    }
}

internal class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        playerConnectionFor(context).previous()
        NowPlayingWidget().update(context, glanceId)
    }
}
