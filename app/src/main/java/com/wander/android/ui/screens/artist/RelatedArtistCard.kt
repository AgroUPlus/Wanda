package com.wander.android.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.RelatedArtist
import com.wander.android.ui.components.Artwork

/**
 * One suggestion from the "Fans might also like" shelf.
 *
 * Round, like the portrait at the top of an artist page, so a card that leads to a person is never
 * mistaken for one that leads to a record — the only visual distinction Wanda draws between the
 * two anywhere else.
 *
 * Opens by *name*, not by id: the artist route is name-keyed so that the page gathers everything
 * by that artist across every connected backend, which no single one of them could do.
 */
@Composable
internal fun RelatedArtistCard(
    artist: RelatedArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(104.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Artwork(
            url = artist.imageUrl,
            contentDescription = artist.name,
            sizeDp = ArtistCardArtwork,
            shape = CircleShape,
            modifier = Modifier.size(ArtistCardArtwork)
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private val ArtistCardArtwork = 88.dp
