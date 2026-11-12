package com.wander.android.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.player.MorphArtworkSize
import com.wander.android.ui.components.player.TurningCookiePlayButton
import com.wander.android.ui.theme.heroOverline

/**
 * The top of the queue drawer: a strip of the playing track's cover that fades into the drawer's
 * own [surfaceColor], so the cover and the list under it read as one surface — the player folded
 * up rather than a second panel stacked on it. What is playing is written on the fade, with the
 * same turning play button the player has.
 *
 * The text takes the surface's own content colours rather than a fixed white: it sits on the part
 * of the gradient that is mostly [surfaceColor], which is light in a light theme. Only the handle,
 * drawn on the raw cover, is a fixed light tint — there is no theme role for "on an arbitrary
 * picture".
 *
 * @param menu the round options button, top right; nothing is drawn when the queue has no options.
 */
@Composable
internal fun QueueCoverStrip(
    track: UnifiedTrack?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit,
    onLongPressTrack: (() -> Unit)?,
    surfaceColor: Color,
    height: Dp,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val fade = remember(surfaceColor) {
        Brush.verticalGradient(
            FADE_START to surfaceColor.copy(alpha = 0f),
            FADE_MID to surfaceColor.copy(alpha = 0.55f),
            FADE_DEEP to surfaceColor.copy(alpha = 0.9f),
            1f to surfaceColor
        )
    }
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Artwork(
            url = track?.artworkUrl,
            contentDescription = null,
            // The player's own request size, so the strip is served from the cover it already loaded.
            sizeDp = MorphArtworkSize,
            shape = RectangleShape,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(fade))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(HandleColor, CircleShape)
            )
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 12.dp)) { menu() }

            if (track != null) {
                NowPlayingLine(
                    track = track,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onTogglePlay = onTogglePlay,
                    onLongPressTrack = onLongPressTrack,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

/** "Now playing", the title and the artist, with play/pause beside them. */
@Composable
private fun NowPlayingLine(
    track: UnifiedTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit,
    onLongPressTrack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // The long press opens the track's actions, as it does on every row below. No press at all
    // when there is no row to act on, rather than a control that does nothing.
    val longPress = onLongPressTrack?.let { open ->
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onLongClickLabel = stringResource(R.string.common_more_options),
            onLongClick = open
        )
    } ?: Modifier
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .then(longPress)
        ) {
            Text(
                text = stringResource(R.string.queue_now_playing),
                style = MaterialTheme.typography.heroOverline,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TurningCookiePlayButton(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onToggle = onTogglePlay,
            size = PlayButtonSize,
            iconSize = PlayIconSize,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Where the cover starts to fade, and how deep it is by two points on the way down. */
private const val FADE_START = 0.14f
private const val FADE_MID = 0.45f
private const val FADE_DEEP = 0.8f

/** Light on the cover, as the system's own handles are on media. */
private val HandleColor = Color.White.copy(alpha = 0.75f)

private val PlayButtonSize = 64.dp
private val PlayIconSize = 28.dp
