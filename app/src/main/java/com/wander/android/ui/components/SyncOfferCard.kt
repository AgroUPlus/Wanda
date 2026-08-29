package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.SyncRoute

/**
 * An offer of tracks another device on the account has and this one does not.
 *
 * Drawn from the theme rather than from literals: corner radii come from `MaterialTheme.shapes`,
 * the button takes `ButtonDefaults.shapes()` like the other call sites, and weights come from the
 * type scale instead of being set by hand.
 *
 * The card is a **summary**, not a listing. It says how many tracks and where they are coming
 * from; tapping it opens the full list. Three lines of titles squeezed beside a button could never
 * say much, and what it did say was cut off — the detail belongs somewhere with room for it.
 *
 * The route is named by an icon rather than an emoji, matching every other status in the app.
 */
@Composable
internal fun SyncOfferCard(
    count: Int,
    covers: List<String>,
    route: SyncRoute?,
    peerName: String?,
    isFetching: Boolean,
    /** How far through, 0..1. Null before anything starts. */
    progress: Float?,
    onAccept: () -> Unit,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            CoverCollage(urls = covers, size = 44.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (count == 1) "1 track available" else "$count tracks available",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                if (route != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = route.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            // Only the direct route names the peer. Over a relay the bytes come
                            // from the server, and naming the far device would suggest a
                            // connection to it that does not exist.
                            text = if (route == SyncRoute.DIRECT && peerName != null) {
                                "$peerName · ${route.label}"
                            } else {
                                route.label
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.scrollingTitle()
                        )
                    }
                }

                // The same wavy bar the player uses, so "something is moving" reads the same way
                // everywhere in the app.
                if (progress != null) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = onAccept,
                enabled = !isFetching,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                // Centred rather than left-aligned: "Syncing…" is three times the width of "Get",
                // and a start-aligned label made the button's text jump sideways on every tap.
                Text(
                    text = if (isFetching) "Syncing…" else "Get",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** The glyph for a route. The names live in the data layer, where the route is decided. */
internal val SyncRoute.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        SyncRoute.DIRECT -> Icons.Rounded.Bolt
        SyncRoute.ARCHIVE -> Icons.Rounded.Cloud
        SyncRoute.RELAY -> Icons.Rounded.Language
    }
