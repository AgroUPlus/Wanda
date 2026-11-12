package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork

/**
 * The foot of the player: what plays next, and the way into the queue.
 *
 * Always drawn, so the panel never changes height between tracks. With nothing queued after this
 * song it says what happens instead — radio keeps going, or the queue simply ends — rather than
 * leaving a hole where the bar was.
 */
@Composable
internal fun UpNextBar(
    next: UnifiedTrack?,
    isRadioMode: Boolean,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.player_next_up_label)
    val detail = when {
        next == null -> stringResource(if (isRadioMode) R.string.player_next_up_radio else R.string.player_next_up_end)
        next.artist.isBlank() -> next.title
        else -> stringResource(R.string.player_next_up_track, next.title, next.artist)
    }
    val description = when {
        next == null -> stringResource(R.string.player_next_up_fallback_description, detail)
        next.artist.isBlank() -> stringResource(R.string.player_next_up_description_title_only, next.title)
        else -> stringResource(R.string.player_next_up_description, next.title, next.artist)
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onOpenQueue,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .semantics { contentDescription = description }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().padding(start = 6.dp, end = 16.dp)
        ) {
            UpNextThumb(next, isRadioMode)
            Text(
                text = buildAnnotatedString {
                    // A muted "Next" in front of a track it names; a fallback line is a sentence of
                    // its own, muted throughout, since it names nothing.
                    if (next != null) {
                        withStyle(SpanStyle(color = muted)) { append(label) }
                        append("  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(detail) }
                    } else {
                        withStyle(SpanStyle(color = muted)) { append(detail) }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The bar already says all of this, in one sentence, for a screen reader.
                modifier = Modifier.weight(1f).clearAndSetSemantics { }
            )
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = muted)
        }
    }
}

/** The next track's cover, or — with none — what happens instead: radio, or the queue's end. */
@Composable
private fun UpNextThumb(next: UnifiedTrack?, isRadioMode: Boolean) {
    if (next != null) {
        Artwork(
            url = next.artworkUrl,
            contentDescription = null,
            sizeDp = ThumbSize,
            shape = CircleShape,
            modifier = Modifier.size(ThumbSize)
        )
    } else {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ThumbSize)) {
            Icon(
                imageVector = if (isRadioMode) Icons.Rounded.Radio else Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val BarHeight = 52.dp
private val ThumbSize = 40.dp
