package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The cover-skip carousel (`TrackSwipe.kt`/`PeekArtwork.kt`, simplified): the current cover slides
 * fully out while the next one slides fully in from the same side, in the same slot [PhoneMock]'s
 * cover box already sized and clipped — this composable only ever draws the two faces, never the
 * box around them.
 *
 * [progress] is 0 at the current cover's resting position and 1 once the swap has completed; the
 * real filmstrip's tilt and neighbour-peeking are left out, since this is meant to read at a
 * glance, not reproduce the drag physics.
 */
@Composable
internal fun BoxScope.SkipCoverSwap(progress: Float) {
    CoverFace(
        modifier = Modifier.graphicsLayer {
            translationX = -size.width * progress
            alpha = 1f - progress
        }
    )
    CoverFace(
        modifier = Modifier.graphicsLayer {
            translationX = size.width * (1f - progress)
            alpha = progress
        }
    )
}

/** Both faces are the cover — the gesture's whole target — so both use the "is-target" tinting. */
@Composable
private fun CoverFace(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = coverIconColor(scheme, isTarget = true)
        )
    }
}
