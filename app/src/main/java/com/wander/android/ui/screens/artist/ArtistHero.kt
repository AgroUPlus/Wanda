package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.ShapedActionButton
import com.wander.android.ui.components.ShapedPlayButton

/**
 * The top of an artist page: portrait, name, and the things you can do with them.
 *
 * Laid out to match `DetailHeader` rather than against it. The two headers used to differ in
 * layout *and* in which controls were filled versus outlined, so an album page and an artist page
 * disagreed about which button mattered — nothing about a record versus a person justifies that.
 * What still differs is the one thing that should: the artwork is a circle, because an artist has
 * no sleeve of their own, and the round crop is what tells a person from a record at a glance.
 *
 * The portrait is the backend's when it publishes one, otherwise a cover off one of their records.
 * Wanda has no artist photography and does not invent any.
 */
@Composable
internal fun ArtistHero(
    name: String,
    subtitle: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null until a track has loaded with a backend artist id — see `ArtistViewModel`. */
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
                    url = imageUrl,
                    contentDescription = name,
                    sizeDp = PortraitSize,
                    shape = CircleShape,
                    modifier = Modifier.size(PortraitSize)
                )

                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ShapedActionButton(
                    onClick = onRadio,
                    contentDescription = "Start radio",
                    icon = Icons.Rounded.Podcasts
                )
                onShare?.let { share ->
                    ShapedActionButton(
                        onClick = share,
                        contentDescription = "Share",
                        icon = Icons.Rounded.Share
                    )
                }
                // Pushed to the trailing edge, alone: the one control the page exists for.
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

internal val PortraitSize = 96.dp
