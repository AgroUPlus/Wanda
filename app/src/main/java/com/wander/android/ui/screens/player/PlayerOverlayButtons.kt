package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two buttons floating over the cover/lyrics square: share, and the artwork/lyrics toggle.
 *
 * Over the cover they need a filled container to stay legible against whatever the artwork happens
 * to be. Over the lyrics they have plain background behind them and the container only gets in the
 * way of reading, so it fades out and leaves the icon on its own.
 *
 * [contentAlpha] is a lambda so the player-sheet fade is read during draw rather than composition —
 * the sheet's progress changes every frame of a drag.
 *
 * It is deliberately a *steeper* ramp than the rest of the full player. These two sit on the cover
 * but are not drawn with it — the travelling cover is painted above this layout, so the square
 * these are aligned to never moves. On the way down the cover left immediately while the buttons
 * hung in place over nothing, which is exactly what looked broken. They now go first.
 */
@Composable
internal fun BoxScope.PlayerOverlayButtons(
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    /** Null when the track's source cannot publish a link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)?,
    contentAlpha: () -> Float,
    /**
     * Whether these have to inset themselves off the system bars.
     *
     * They do not in the standard layout: there they sit inside the artwork square, which is
     * already inside a column that has taken the window insets for the whole screen. The immersive
     * layout puts them straight onto a full-bleed `Box` with nothing between them and the edge of
     * the display, and the share button landed in the status bar.
     */
    applyWindowInsets: Boolean = false,
    /** Clearance for whatever the caller has drawn along the top edge, above the 12 dp margin. */
    topInset: Dp = 0.dp,
    /** The same for the bottom edge — controls, a seek bar — under the lyrics toggle. */
    bottomInset: Dp = 0.dp
) {
    val colors = overlayButtonColors(showLyrics)
    val insets = if (applyWindowInsets) Modifier.safeDrawingPadding() else Modifier

    onShare?.let { share ->
        FilledTonalIconButton(
            onClick = share,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(insets)
                .padding(top = topInset)
                .padding(12.dp)
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share a link to this track"
            )
        }
    }

    FilledTonalIconButton(
        onClick = onToggleLyrics,
        colors = colors,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .then(insets)
            .padding(bottom = bottomInset)
            .padding(12.dp)
            .graphicsLayer { alpha = contentAlpha() }
    ) {
        Icon(
            imageVector = if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
            contentDescription = if (showLyrics) "Show artwork" else "Show lyrics"
        )
    }
}

/** Animated so the lyrics toggle carries the buttons across rather than cutting them. */
@Composable
private fun overlayButtonColors(showLyrics: Boolean): IconButtonColors {
    val base = IconButtonDefaults.filledTonalIconButtonColors()
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue = if (showLyrics) Color.Transparent else base.containerColor,
        animationSpec = spec,
        label = "overlay-button-container"
    )
    val content by animateColorAsState(
        targetValue = if (showLyrics) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            base.contentColor
        },
        animationSpec = spec,
        label = "overlay-button-content"
    )
    return base.copy(containerColor = container, contentColor = content)
}
