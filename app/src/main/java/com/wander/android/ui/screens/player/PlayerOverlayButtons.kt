package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
 * The share button floating over the cover/lyrics square.
 *
 * Over the cover it needs a filled container to stay legible against whatever the artwork happens
 * to be. Over the lyrics it has plain background behind it and the container only gets in the way
 * of reading, so it fades out and leaves the icon on its own.
 *
 * It used to have a twin at the bottom that toggled the lyrics. The cover itself does that now —
 * tapping it is the whole gesture — so the button is gone rather than duplicating a tap target the
 * artwork already is. That also frees the bottom-right corner it kept having to be inset out of.
 *
 * [contentAlpha] is a lambda so the player-sheet fade is read during draw rather than composition —
 * the sheet's progress changes every frame of a drag.
 *
 * It is deliberately a *steeper* ramp than the rest of the full player. This sits on the cover but
 * is not drawn with it — the travelling cover is painted above this layout, so the square it is
 * aligned to never moves. On the way down the cover left immediately while the button hung in place
 * over nothing, which is exactly what looked broken. It now goes first.
 */
@Composable
internal fun BoxScope.PlayerOverlayButtons(
    showLyrics: Boolean,
    /** Null when the track's source cannot publish a link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)?,
    contentAlpha: () -> Float,
    /**
     * How much of the container's top edge is already occupied, measured rather than assumed.
     *
     * Zero in the standard layout, where this sits inside the artwork square and the column around
     * it has already taken the window insets for the whole screen. The immersive layout draws its
     * own bar straight onto a full-bleed `Box`, and passes what it actually measured — including
     * the system bars, which are inside that measurement.
     *
     * It was a constant written from the heights the layouts are built out of. It was wrong, and
     * the sibling that read the bottom edge the same way put the lyrics toggle on the like button.
     */
    topInset: Dp = 0.dp
) {
    val colors = overlayButtonColors(showLyrics)

    onShare?.let { share ->
        FilledTonalIconButton(
            onClick = share,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopEnd)
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
}

/** Animated so showing the lyrics carries the button across rather than cutting it. */
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
