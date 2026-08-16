package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
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
 */
@Composable
internal fun BoxScope.PlayerOverlayButtons(
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    /** Null when the track's source cannot publish a link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)?,
    contentAlpha: () -> Float
) {
    val colors = overlayButtonColors(showLyrics)

    onShare?.let { share ->
        FilledTonalIconButton(
            onClick = share,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopEnd)
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
