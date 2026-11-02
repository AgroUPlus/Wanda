package com.wander.android.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import com.wander.android.R

/**
 * The Now Playing widget: cover, title, artist and source, and transport as icons. A tap anywhere
 * but the buttons opens the app.
 *
 * Responsive rather than one fixed layout: a 2×1 shows the cover, the title and play/pause; a 4×1
 * adds previous and next; a 4×2 stacks the transport under a larger cover. See
 * [NowPlayingWidgetContent].
 *
 * What it shows is pushed by the playback service into the widget's Glance state — see
 * [WidgetSnapshot] — so drawing it binds nothing. The cover is fetched through the app's Coil
 * loader as a small bitmap (see [loadWidgetArtwork]) whenever the artwork URL changes.
 *
 * [GlanceTheme] is what gives this Material You dynamic tinting on API 31+ with a static fallback
 * below it — the same tradeoff [com.wander.android.ui.components.player.FluidAmbientShader] makes
 * for the AGSL shader elsewhere in this suite, just for a widget host instead of an OS version.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read here, not with `stringResource` inside the content below: Glance's `provideContent`
        // composables don't run in a normal Compose/resources context the way an Activity's do.
        val labels = WidgetLabels(
            nothingPlaying = context.getString(R.string.widget_nothing_playing),
            previous = context.getString(R.string.action_previous),
            next = context.getString(R.string.widget_next),
            play = context.getString(R.string.widget_play),
            pause = context.getString(R.string.widget_pause)
        )

        provideContent {
            val snapshot = WidgetSnapshot.from(currentState<Preferences>())
            val artwork: Bitmap? by produceState<Bitmap?>(null, snapshot.artworkUrl) {
                value = snapshot.artworkUrl?.let { loadWidgetArtwork(context, it) }
            }
            GlanceTheme {
                NowPlayingWidgetContent(snapshot, artwork, labels)
            }
        }
    }

    internal companion object {
        /** 2×1: cover, title, play/pause. */
        val COMPACT = DpSize(110.dp, 40.dp)

        /** 4×1: the same row, with previous and next. */
        val WIDE = DpSize(250.dp, 40.dp)

        /** 4×2: a larger cover with title and artist, transport underneath. */
        val LARGE = DpSize(250.dp, 110.dp)
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

/** The widget's text, resolved once per session; see [NowPlayingWidget.provideGlance]. */
internal class WidgetLabels(
    val nothingPlaying: String,
    val previous: String,
    val next: String,
    val play: String,
    val pause: String
)
