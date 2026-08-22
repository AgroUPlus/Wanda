package com.wander.android.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.isPlayableNow

/**
 * Spotify-style horizontal media card for carousels (Heavy Rotation, Recently Played).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalTrackCard(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Long press, for the track actions sheet. Matches [com.wander.android.ui.components.TrackRow]. */
    onLongPress: (() -> Unit)? = null,
    /** Whether tapping would play anything. Defaults to the offline rule, as `TrackRow` does. */
    enabled: Boolean = track.isPlayableNow()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "trackCardPress"
    )

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            // Deliberately no clip on the card: rounding the whole Column cropped the corners off
            // the title and artist underneath. The artwork rounds itself via its own shape.
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = 140.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // No quality badge here: it belongs to the track you are listening to, not to every card
        // on the shelf. Now Playing is the one place that shows it.
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f
