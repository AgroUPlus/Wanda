package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.ShapedActionButton
import com.wander.android.ui.components.ShapedPlayButton

/**
 * The top of an album page: the sleeve, at the size a sleeve is worth looking at.
 *
 * This was a 96dp thumbnail in a card with the title beside it — the layout a *list row* uses,
 * scaled up slightly and given buttons. A record's cover is the one piece of artwork the page has
 * and the thing the user recognises it by, so it leads, full width and square, and the type sits
 * under it rather than competing for the same line.
 *
 * No surrounding card. The cover is its own container — a card behind it only drew a second,
 * slightly larger rectangle around a rectangle, and the shadow does the lifting instead.
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = title,
            sizeDp = CoverSize,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .size(CoverSize)
                .shadow(
                    elevation = 18.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    clip = false
                )
        )

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 24.dp)
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

        // Centred under the cover rather than pushed to the edges. With the artwork centred above
        // them, actions pinned left and right read as belonging to the screen instead of to the
        // record — and Play keeps its size advantage, which is what states the hierarchy here.
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 22.dp)
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
 * Big enough to be the page's subject, short enough that the first track is still on screen under
 * it on a normal phone — the tracklist is the other half of what this page is for.
 */
internal val CoverSize = 260.dp
