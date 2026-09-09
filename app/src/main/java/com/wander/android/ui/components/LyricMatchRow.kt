package com.wander.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.data.model.LyricMatch

/**
 * Renders a track matched via lyrics with its highlighted quote and timestamp seek trigger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricMatchRow(
    match: LyricMatch,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleLike: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val track = match.track
    val timeLabel = match.timestampMs?.let { ms ->
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        "%d:%02d".format(min, sec)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            sizeDp = 52.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(52.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                track.album?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Lyric Quote snippet container
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                if (timeLabel != null) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(4.dp))
                }

                val cleanQuote = match.matchedLine.ifBlank {
                    match.snippet.replace(Regex("</?[^>]+(>|$)"), "")
                }

                Text(
                    text = "“$cleanQuote”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }

        if (onToggleLike != null) {
            LikeButton(isLiked = track.isLiked, onToggle = onToggleLike)
        }
    }
}
