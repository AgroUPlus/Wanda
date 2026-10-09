package com.wander.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.isPlayableNow
import com.wander.android.ui.components.scrollingTitle

/**
 * One masked carousel item: artwork under a scrim, title over it, and optionally the artist.
 * Shared by every carousel shelf on Home so they read as one family.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CarouselItemScope.TrackCarouselCard(
    track: UnifiedTrack,
    artworkSize: Dp,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    showArtist: Boolean = false,
    enabled: Boolean = track.isPlayableNow()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = artworkSize,
            // The outer `maskClip` already shapes this card — a second, independent clip here
            // (Artwork's own default `medium` shape) rounded the corners twice at two different
            // radii as the carousel resized the item, which looked like a seam, not one card.
            shape = RectangleShape,
            modifier = Modifier.fillMaxSize()
        )
        // A scrim rather than a solid strip under the text: the whole point of a carousel item is
        // the artwork, and a title readable only over a flat bar would fight it for space at a
        // width some items shrink to as small as `minSmallItemWidth`.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = ScrimAlpha))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            if (showArtist) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = ArtistAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f
private const val ScrimAlpha = 0.65f
private const val ArtistAlpha = 0.8f
