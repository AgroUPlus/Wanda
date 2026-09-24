package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.ImmersiveHero

/** A band, not a page opener — there is a list under this and it is the point of the screen. */
private const val ActivityAspect = 2.4f

/** Matches the sender's avatar on the row above, so the two kinds of card line up. */
private val ReleaseArtSize = 40.dp

/**
 * A song somebody handed you, said as that rather than as a message.
 */
@Composable
internal fun SharedWithMeCard(
    item: ActivityItem.Shared,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drop = item.drop
    val unread = drop.readAt == null

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (unread) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            CuteAvatar(seed = drop.fromUser, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@" + drop.fromUser + " sent you a song",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = drop.trackTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = drop.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Something a followed artist put out.
 */
@Composable
internal fun NewReleaseCard(
    item: ActivityItem.Release,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val release = item.release

    Card(
        onClick = onOpen ?: {},
        enabled = onOpen != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            val artwork = item.artworkUrl
            if (artwork != null) {
                Artwork(
                    url = artwork,
                    contentDescription = null,
                    sizeDp = ReleaseArtSize,
                    shape = MaterialTheme.shapes.small,
                    crossfade = true,
                    modifier = Modifier.size(ReleaseArtSize)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.social_new_from, release.artist),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = release.title ?: release.album ?: "Something new",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The top of the screen, on the shape the rest of the app opens on. */
@Composable
internal fun ActivityHero(
    unread: Int,
    onBack: () -> Unit,
    onOpenCircleRecap: () -> Unit
) {
    val start = MaterialTheme.colorScheme.primaryContainer
    val end = MaterialTheme.colorScheme.surfaceContainerHighest

    ImmersiveHero(
        aspect = ActivityAspect,
        scrimHeight = 12.dp,
        backdrop = {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(start, end)))
            )
        },
        overlay = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription =
                        stringResource(R.string.common_back))
                }
                FilledTonalIconButton(onClick = onOpenCircleRecap) {
                    Icon(Icons.Rounded.QueryStats, contentDescription = stringResource(R.string.social_circle_recap))
                }
            }
        },
        caption = {
            Text(
                text = stringResource(R.string.common_activity),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (unread > 0) {
                    "$unread unread · your circle, and what people sent you"
                } else {
                    "Your circle, and what people sent you"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    )
}

/** Says which of the four lists is empty, because "nothing here" under a filter is ambiguous. */
@Composable
internal fun ActivityEmptyState(filter: ActivityFilter, releasesUnsupported: Boolean) {
    val message = when {
        releasesUnsupported && filter == ActivityFilter.RELEASES ->
            "This Agro server is too old to know about artist subscriptions. Update the server " +
                "to follow artists and see their releases here."

        filter == ActivityFilter.ALL ->
            "Nothing yet. Friends show up here once they share activity, or send you something."
        filter == ActivityFilter.CIRCLE ->
            "No circle activity. Friends appear here once they turn on activity sharing."
        filter == ActivityFilter.SHARED ->
            "Nobody has sent you a song yet."
        else ->
            "Subscribe to an artist and new releases will land here."
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
