package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Album art with a themed placeholder.
 *
 * [sizeDp] is the edge length this artwork is laid out at. It decides the decode size and the
 * memory-cache bucket, so passing the real size is what keeps one cover from being decoded once
 * per place it appears — pass the size the caller already fixed via its modifier.
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    crossfade: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            NoArtwork(contentDescription)
        } else {
            LoadingImage(
                model = rememberArtworkRequest(url = url, sizeDp = sizeDp, crossfade = crossfade),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // A cover that would not load is a cover this track has none of, as far as the
                // screen can tell — the same note as one that never had a URL.
                error = { NoArtwork(contentDescription) }
            )
        }
    }
}

@Composable
private fun NoArtwork(contentDescription: String?) {
    Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
