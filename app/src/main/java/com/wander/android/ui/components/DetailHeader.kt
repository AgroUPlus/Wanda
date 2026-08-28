package com.wander.android.ui.components

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Edge of the hero artwork on a detail page. Also its decode size. */
private val HeroSize = 96.dp

/**
 * The top of an album page: hero artwork, title, a line of metadata, and the actions.
 *
 * Shares its layout with `ArtistHero` — same card, same control row, same sizes. The two used to
 * differ in which buttons were filled and which outlined, so the album page said "Play" was the
 * important control and the artist page said it was "Shuffle", for no reason either page could
 * justify. Now the shaped play button is the emphasis on both, and the shape of the artwork is the
 * only thing that distinguishes a record from a person.
 */
@Composable
internal fun DetailHeader(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    artworkShape: Shape,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null when this record's backend cannot publish a link for it. */
    onShare: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    url = artworkUrl,
                    contentDescription = title,
                    sizeDp = HeroSize,
                    shape = artworkShape,
                    modifier = Modifier.size(HeroSize)
                )

                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                ShapedActionButton(
                    onClick = onShuffle,
                    contentDescription = "Shuffle",
                    icon = Icons.Rounded.Shuffle
                )
                onShare?.let { share ->
                    ShapedActionButton(
                        onClick = share,
                        contentDescription = "Share",
                        icon = Icons.Rounded.Share
                    )
                }
                Column(modifier = Modifier.weight(1f)) {}
                ShapedPlayButton(
                    onClick = onPlay,
                    contentDescription = "Play",
                    icon = Icons.Rounded.PlayArrow
                )
            }
        }
    }
}
