package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Everything drawn over the visible part of the cover: the top bar, the lyrics when they are up,
 * and the lyrics pill in the bottom corner (with a way to the full-screen lyrics beside it while
 * they show).
 *
 * Sized by the caller to the cover's *visible* area — the part above the controls panel — so
 * "bottom corner" means just above the panel's edge in both layouts. It takes the top window inset
 * itself: the cover runs up behind the status bar, its buttons must not.
 *
 * Holds no pointer input of its own, so a tap on bare cover still reaches the cover's gestures
 * beneath it; only the buttons and, while they show, the lyrics take touches here.
 */
@Composable
internal fun PlayerCoverOverlay(
    status: PlayerStatus,
    showLyrics: Boolean,
    lyrics: @Composable () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onMinimize: () -> Unit,
    onOpenMenu: () -> Unit,
    contentAlpha: () -> Float,
    overlayAlpha: () -> Float,
    modifier: Modifier = Modifier
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    ) {
        PlayerTopBar(
            status = status,
            onMinimize = onMinimize,
            onOpenMenu = onOpenMenu,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(effects),
                exit = fadeOut(effects),
                modifier = Modifier.fillMaxSize()
            ) {
                lyrics()
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.End)
                .graphicsLayer { alpha = overlayAlpha() }
        ) {
            AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(effects) + scaleIn(spatial),
                exit = fadeOut(effects) + scaleOut(spatial)
            ) {
                FilledTonalIconButton(
                    onClick = onOpenFullScreenLyrics,
                    colors = playerOverlayButtonColors(),
                    modifier = Modifier.size(PillHeight)
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.player_lyrics_full_screen)
                    )
                }
            }
            LyricsPill(showLyrics = showLyrics, onToggle = onToggleLyrics)
        }
    }
}

/**
 * The lyrics toggle: the same fixed scrim as the other controls over the cover while off, and the
 * primary colour while the lyrics are up, so it says which of the two the cover is showing.
 */
@Composable
private fun LyricsPill(showLyrics: Boolean, onToggle: () -> Unit) {
    val scrim = playerOverlayButtonColors()
    val container by animateColorAsState(
        targetValue = if (showLyrics) MaterialTheme.colorScheme.primary else scrim.containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lyricsPillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (showLyrics) MaterialTheme.colorScheme.onPrimary else scrim.contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lyricsPillContent"
    )
    Surface(
        checked = showLyrics,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.height(PillHeight)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 14.dp)
        ) {
            Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.player_lyrics), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val PillHeight = 36.dp
