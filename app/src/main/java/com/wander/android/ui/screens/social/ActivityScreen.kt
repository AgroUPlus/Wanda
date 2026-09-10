package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.listInset

/**
 * Everything that has happened lately, in one place.
 *
 * This replaces two screens reached from two tiles — the circle's activity and the songs friends
 * had sent — which between them made "has anything happened?" a question you had to ask twice and
 * guess the order of. They are one question, and the answer is one list in the order things
 * actually happened.
 *
 * The circle's recap keeps its own screen, reachable from the header here. A recap is not an event:
 * it is a summary of a period, and dropping a leaderboard between two things that happened on
 * Tuesday would be filing a chart as news.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActivityScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenCircleRecap: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "hero") {
            ActivityHero(
                unread = state.unread,
                onBack = onBack,
                onOpenCircleRecap = onOpenCircleRecap
            )
        }

        // A connected group, the same as the circle's period switch: the filters are mutually
        // exclusive and between them cover everything the list can hold.
        item(key = "filters") {
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                ActivityFilter.entries.forEach { filter ->
                    toggleableItem(
                        checked = state.filter == filter,
                        label = filter.label,
                        onCheckedChange = { viewModel.setFilter(filter) },
                        weight = 1f
                    )
                }
            }
        }

        val visible = state.visible
        if (visible.isEmpty() && !state.loading) {
            item(key = "empty") { ActivityEmptyState(state.filter) }
        }

        items(
            items = visible,
            key = { item ->
                when (item) {
                    is ActivityItem.Milestone -> "m_" + item.item.username + "_" + item.at
                    is ActivityItem.Shared -> "s_" + item.drop.id
                    is ActivityItem.Release -> "r_" + item.release.recordingId
                }
            }
        ) { item ->
            when (item) {
                is ActivityItem.Milestone -> FeedItemCard(
                    item = item.item,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                is ActivityItem.Shared -> SharedWithMeCard(
                    item = item,
                    onOpen = { onOpenThread(item.drop.fromUser) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                is ActivityItem.Release -> NewReleaseCard(
                    item = item,
                    onOpen = { onOpenArtist(item.release.artist) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * A song somebody handed you, said as that rather than as a message.
 *
 * The inbox drew these as chat rows, because that is what the screen was. In a feed the interesting
 * part is the same as every other row's: who, and what about. So the sender leads, by face and by
 * name, and the track is what they are said to have done.
 */
@Composable
private fun SharedWithMeCard(
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
 *
 * Says the artist first, like every other row here: the feed is a list of things people did, and
 * the person is what makes one row different from the next.
 */
@Composable
private fun NewReleaseCard(
    item: ActivityItem.Release,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val release = item.release

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New from " + release.artist,
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
private fun ActivityHero(
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                FilledTonalIconButton(onClick = onOpenCircleRecap) {
                    Icon(Icons.Rounded.QueryStats, contentDescription = "Circle recap")
                }
            }
        },
        caption = {
            Text(
                text = "Activity",
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
private fun ActivityEmptyState(filter: ActivityFilter) {
    val message = when (filter) {
        ActivityFilter.ALL ->
            "Nothing yet. Friends show up here once they share activity, or send you something."
        ActivityFilter.CIRCLE ->
            "No circle activity. Friends appear here once they turn on activity sharing."
        ActivityFilter.SHARED ->
            "Nobody has sent you a song yet."
        ActivityFilter.RELEASES ->
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

/** A band, not a page opener — there is a list under this and it is the point of the screen. */
private const val ActivityAspect = 2.4f
