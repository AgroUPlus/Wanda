package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * A miniature of the real queue drawer (`QueueDrawer.kt`): a title and two mock rows, the current
 * track highlighted. Slides up from below by [liftFraction] (0 hidden under the frame, 1 fully in
 * place) so the swipe-up gesture's own drag progress can drive it directly.
 */
@Composable
internal fun QueueOverlay(liftFraction: Float, modifier: Modifier = Modifier) {
    if (liftFraction <= 0f) return
    val scheme = MaterialTheme.colorScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .graphicsLayer {
                translationY = size.height * (1f - liftFraction)
                alpha = liftFraction
            }
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.action_queue),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        QueueRow(
            title = stringResource(R.string.welcome_gesture_mock_track_title),
            artist = stringResource(R.string.welcome_gesture_mock_track_artist),
            current = true
        )
        QueueRow(
            title = stringResource(R.string.welcome_gesture_mock_track_title_alt),
            artist = stringResource(R.string.welcome_gesture_mock_track_artist_alt),
            current = false
        )
    }
}

@Composable
private fun QueueRow(title: String, artist: String, current: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (current) scheme.primaryContainer.copy(alpha = 0.4f) else scheme.surfaceContainerHigh)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(MaterialTheme.shapes.small)
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
