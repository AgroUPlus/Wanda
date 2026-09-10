package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.ShapedActionButton
import com.wander.android.ui.components.ShapedPlayButton

/**
 * The top of an album or playlist page: the cover, at the size a cover is worth looking at.
 *
 * It began as a 96 dp thumbnail in a card with the title beside it — a list row scaled up and
 * given buttons — then became a centred 260 dp square. It is the full width of the window now,
 * running to the top of it, with the title set into the foot of the artwork. See [ImmersiveHero]
 * for why the caption needs no scrim and why the artist page and the statistics screen open the
 * same way.
 *
 * Playlists use this too. A playlist's cover is a cover, and giving the two pages different
 * headers would say they were different kinds of thing when the only real difference is who chose
 * the running order.
 */
@Composable
internal fun AlbumHero(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null when this record's backend cannot publish a link for it. */
    onShare: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ImmersiveHero(
            imageUrl = artworkUrl,
            contentDescription = title,
            aspect = CoverAspect,
            horizontalPadding = 24.dp
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Centred under the cover rather than pushed to the edges. With the artwork centred above
        // them, actions pinned left and right read as belonging to the screen instead of to the
        // record — and Play keeps its size advantage, which is what states the hierarchy here.
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp)
        ) {
            ShapedActionButton(
                onClick = onShuffle,
                contentDescription = "Shuffle",
                icon = Icons.Rounded.Shuffle
            )
            ShapedPlayButton(
                onClick = onPlay,
                contentDescription = "Play",
                icon = Icons.Rounded.PlayArrow
            )
            onShare?.let { share ->
                ShapedActionButton(
                    onClick = share,
                    contentDescription = "Share",
                    icon = Icons.Rounded.Share
                )
            }
        }
    }
}

/**
 * A little taller than the artwork itself is.
 *
 * A cover is square, so a square hero would put the caption across the middle of it. The extra
 * height is the room the title needs without covering the record's own centre.
 */
internal const val CoverAspect = 0.86f
