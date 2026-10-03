package com.wander.android.ui.screens.queue

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.KineticEqualizer
import com.wander.android.ui.components.rememberPressMorphShape
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.theme.heroOverline

/**
 * The track playing now, at the top of the queue and outside its list.
 *
 * It used to be one more row in the list, tinted — the same size as every track around it, so
 * finding "where am I" meant scanning for a colour. As a card it is the anchor the rest of the
 * queue hangs off: what is playing, then what comes next. It is not dragged or swiped; only the
 * tracks around it are.
 *
 * Its cover sits in a cookie, Material 3 Expressive's own shape, and relaxes into a circle under a
 * finger, like every cover in the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QueueNowPlayingCard(
    track: UnifiedTrack,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource, label = "queueNowPlayingPress")
    val coverShape = rememberPressMorphShape(MaterialShapes.Cookie9Sided, MaterialShapes.Circle, isPressed)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                sizeDp = CoverSize,
                shape = coverShape,
                modifier = Modifier.size(CoverSize)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.queue_now_playing),
                    style = MaterialTheme.typography.heroOverline,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (track.artist.isNotBlank()) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            KineticEqualizer(isPlaying = true, maxHeight = 20.dp)
        }
    }
}

private val CoverSize = 76.dp
