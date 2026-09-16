package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.wander.android.data.model.LyricsSyncType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The buttons floating over the cover/lyrics square: share, and the artwork/lyrics toggle.
 *
 * Over the cover they need a filled container to stay legible against whatever the artwork happens
 * to be. Over the lyrics they have plain background behind them and the container only gets in the
 * way of reading, so it fades out and leaves the icon on its own.
 *
 * Tapping the cover toggles the lyrics in both layouts. The button is the visible way to say so,
 * and it is offered only where there is somewhere to put it: in the standard layout it sits in the
 * corner of a bounded square, clear of everything. The immersive layout has no such corner — the
 * artwork is the whole window, the bottom-right belongs to the controls, and this is the button
 * that once came down on top of the like button trying to share that space. There it passes null
 * and the cover carries the gesture alone.
 *
 * [contentAlpha] is a lambda so the player-sheet fade is read during draw rather than composition —
 * the sheet's progress changes every frame of a drag.
 *
 * It is deliberately a *steeper* ramp than the rest of the full player. These sit on the cover but
 * are not drawn with it — the travelling cover is painted above this layout, so the square they are
 * aligned to never moves. On the way down the cover left immediately while the buttons hung in
 * place over nothing, which is exactly what looked broken. They now go first.
 */
@Composable
internal fun BoxScope.PlayerOverlayButtons(
    showLyrics: Boolean,
    /** Null when the track's source cannot publish a link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)?,
    contentAlpha: () -> Float,
    /**
     * Null where the layout has no room for it, and the cover's own tap is the only way through.
     * See the note on this function.
     */
    onToggleLyrics: (() -> Unit)? = null,
    topInset: Dp = 0.dp,
    lyricsSyncType: LyricsSyncType = LyricsSyncType.NONE
) {
    val colors = overlayButtonColors(showLyrics)

    // Gone entirely with the lyrics up, not merely faded to a bare glyph. Over the cover it sits on
    // artwork and needs its container to stay legible; over the lyrics it sits on text it is
    // covering, and a share control is not what anyone reached for when they asked to read along.
    // The toggle below stays, because that is the way back.
    onShare?.takeIf { !showLyrics }?.let { share ->
        FilledTonalIconButton(
            onClick = share,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topInset)
                .padding(12.dp)
                .size(OverlayButtonSize)
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.action_share_track),
                modifier = Modifier.size(OverlayIconSize)
            )
        }
    }

    onToggleLyrics?.let { toggle ->
        val lyricsIcon = when {
            showLyrics -> Icons.Rounded.Album
            lyricsSyncType == LyricsSyncType.WORD_SYNCED -> Icons.Rounded.AutoAwesome
            lyricsSyncType == LyricsSyncType.LINE_SYNCED -> Icons.Rounded.Lyrics
            else -> Icons.Rounded.Description
        }
        val lyricsDescription = when {
            showLyrics -> stringResource(R.string.lyrics_show_artwork)
            lyricsSyncType == LyricsSyncType.WORD_SYNCED -> stringResource(R.string.lyrics_show_word_synced)
            lyricsSyncType == LyricsSyncType.LINE_SYNCED -> stringResource(R.string.lyrics_show_synced)
            else -> stringResource(R.string.lyrics_show_plain)
        }

        FilledTonalIconButton(
            onClick = toggle,
            colors = colors,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(OverlayButtonSize)
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            Icon(
                imageVector = lyricsIcon,
                contentDescription = lyricsDescription,
                modifier = Modifier.size(OverlayIconSize)
            )
        }
    }
}

private val OverlayButtonSize = 52.dp
private val OverlayIconSize = 28.dp

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
