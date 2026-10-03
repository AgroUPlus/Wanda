package com.wander.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * A remote image that breathes like a [SkeletonBox] until it lands.
 *
 * Every cover, collage tile and avatar used to sit as a flat block while it loaded, which reads the
 * same as an image that is never coming. The placeholder is drawn *behind* the image rather than
 * swapped for it, so a memory-cache hit that resolves a frame after composition covers it at once
 * instead of flashing. Once the load settles the placeholder leaves composition, and its animation
 * with it — nothing keeps ticking behind a finished image.
 *
 * [error] is drawn when the load fails, in place of the empty background that used to stand there.
 */
@Composable
internal fun LoadingImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    error: @Composable BoxScope.() -> Unit = {}
) {
    var state by remember(model) { mutableStateOf<LoadState>(LoadState.Loading) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (state) {
            LoadState.Loading -> SkeletonBox(Modifier.matchParentSize(), shape = RectangleShape)
            LoadState.Failed -> error()
            LoadState.Loaded -> Unit
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { painterState ->
                state = when (painterState) {
                    is AsyncImagePainter.State.Success -> LoadState.Loaded
                    is AsyncImagePainter.State.Error -> LoadState.Failed
                    is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> LoadState.Loading
                }
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

private enum class LoadState { Loading, Loaded, Failed }
