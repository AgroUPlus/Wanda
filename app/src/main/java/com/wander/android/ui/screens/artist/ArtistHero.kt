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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork

/**
 * The top of an artist page: a large round portrait, the name at display size, then the three
 * things you can do with an artist.
 *
 * Deliberately not `DetailHeader`, which albums and playlists share: an artist is the one detail
 * page with no cover of its own, so it leans on scale and a round crop instead of a sleeve. The
 * portrait is whatever cover art the artist's records carry — Wanda has no artist photography,
 * and does not invent any.
 */
@Composable
internal fun ArtistHero(
    name: String,
    subtitle: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Artwork(
            url = imageUrl,
            contentDescription = name,
            sizeDp = PortraitSize,
            shape = CircleShape,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally)
                .size(PortraitSize)
        )

        Text(
            text = name,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            OutlinedButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(text = "Play", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onRadio) {
                Icon(
                    imageVector = Icons.Rounded.Podcasts,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(text = "Radio", modifier = Modifier.padding(start = 8.dp))
            }
            // The one filled control on the page. Shuffle is the tap an artist page most often
            // gets, so it is the one that reads as a button rather than an option.
            FilledIconButton(onClick = onShuffle) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
            }
        }
    }
}

private val PortraitSize = 220.dp
