package com.wander.android.ui.widget

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wander.android.MainActivity
import com.wander.android.R

/** Picks the layout for the size the launcher gave the widget; see [NowPlayingWidget.sizeMode]. */
@Composable
internal fun NowPlayingWidgetContent(snapshot: WidgetSnapshot, artwork: Bitmap?, labels: WidgetLabels) {
    val size = LocalSize.current
    val tall = size.height >= NowPlayingWidget.LARGE.height
    val wide = size.width >= NowPlayingWidget.WIDE.width
    val openApp = actionStartActivity<MainActivity>()
    val root = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.surface)
        .cornerRadius(16.dp)
        .padding(8.dp)

    if (tall) {
        Column(modifier = root, verticalAlignment = Alignment.Vertical.CenterVertically) {
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openApp)
            ) {
                Artwork(artwork, 64.dp)
                Spacer(GlanceModifier.width(12.dp))
                Titles(snapshot, labels, maxLines = 2)
            }
            Row(
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Transport(snapshot, labels, withSkips = true)
            }
        }
    } else {
        Row(modifier = root, verticalAlignment = Alignment.Vertical.CenterVertically) {
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                modifier = GlanceModifier.defaultWeight().clickable(openApp)
            ) {
                Artwork(artwork, 40.dp)
                Spacer(GlanceModifier.width(8.dp))
                Titles(snapshot, labels, maxLines = 1)
            }
            Transport(snapshot, labels, withSkips = wide)
        }
    }
}

@Composable
private fun Artwork(artwork: Bitmap?, size: Dp) {
    val modifier = GlanceModifier.size(size).cornerRadius(8.dp)
    if (artwork != null) {
        Image(
            provider = ImageProvider(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = modifier.background(GlanceTheme.colors.surfaceVariant).padding(size / 4)
        )
    }
}

@Composable
private fun Titles(snapshot: WidgetSnapshot, labels: WidgetLabels, maxLines: Int) {
    Column {
        val title = snapshot.title
        if (title == null) {
            Text(labels.nothingPlaying, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            return@Column
        }
        Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface), maxLines = maxLines)
        snapshot.subtitle?.let {
            Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
    }
}

@Composable
private fun Transport(snapshot: WidgetSnapshot, labels: WidgetLabels, withSkips: Boolean) {
    if (withSkips) {
        TransportButton(R.drawable.ic_widget_skip_previous, labels.previous, actionRunCallback<PreviousAction>())
    }
    TransportButton(
        icon = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        label = if (snapshot.isPlaying) labels.pause else labels.play,
        action = actionRunCallback<TogglePlayPauseAction>(),
        emphasised = true
    )
    if (withSkips) {
        TransportButton(R.drawable.ic_widget_skip_next, labels.next, actionRunCallback<NextAction>())
    }
}

/** A 40 dp touch target around a 24 dp icon; play/pause sits on a filled circle. */
@Composable
private fun TransportButton(@DrawableRes icon: Int, label: String, action: Action, emphasised: Boolean = false) {
    val base = GlanceModifier.size(40.dp).cornerRadius(20.dp)
    Image(
        provider = ImageProvider(icon),
        contentDescription = label,
        colorFilter = ColorFilter.tint(
            if (emphasised) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
        ),
        modifier = (if (emphasised) base.background(GlanceTheme.colors.primaryContainer) else base)
            .clickable(action)
            .padding(8.dp)
    )
}
