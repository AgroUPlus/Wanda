package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The share button, floating in the top-right corner of the cover.
 *
 * It used to share this space with a lyrics toggle, and the pair of them carried a fair amount of
 * machinery: the toggle swapped the lyrics in where the cover was, so both buttons had to
 * cross-fade their containers away to stay off the text they were then sitting on, and the toggle
 * had four icons for the four kinds of lyrics a track might have. None of that is needed now —
 * lyrics open as their own screen (see `FullScreenLyrics`), opened by tapping the cover, so this
 * button only ever sits on artwork and only ever does one thing.
 *
 * [contentAlpha] is a lambda so the player-sheet fade is read during draw rather than composition —
 * the sheet's progress changes every frame of a drag.
 *
 * It is deliberately a *steeper* ramp than the rest of the full player. This sits on the cover but
 * is not drawn with it — the travelling cover is painted above this layout, so the square it is
 * aligned to never moves. On the way down the cover left immediately while the button hung in
 * place over nothing, which is exactly what looked broken. It goes first.
 */
@Composable
internal fun BoxScope.PlayerOverlayButtons(
    /** Null when the track's source cannot publish a link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)?,
    contentAlpha: () -> Float,
    topInset: Dp = 0.dp
) {
    val share = onShare ?: return

    FilledTonalIconButton(
        onClick = share,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topInset)
            .padding(12.dp)
            .graphicsLayer { alpha = contentAlpha() }
    ) {
        Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.action_share_track)
        )
    }
}
