package com.wander.android.ui.screens.social

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.listInset

/**
 * Everything that has happened lately, in one place.
 *
 * Displays circle activity, friend track shares, and new artist releases in a unified chronological feed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActivityScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenCircleRecap: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenArtist: (artist: String, artistId: String?) -> Unit = { _, _ -> },
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

        item(key = "filters") {
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
            ) {
                ActivityFilter.entries.forEach { filter ->
                    toggleableItem(
                        checked = state.filter == filter,
                        label = filter.label,
                        onCheckedChange = { viewModel.setFilter(filter) }
                    )
                }
            }
        }

        val visible = state.visible
        if (visible.isEmpty() && !state.loading) {
            item(key = "empty") {
                ActivityEmptyState(
                    filter = state.filter,
                    releasesUnsupported = state.releasesUnsupported
                )
            }
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
                    onOpen = item.release.artist.takeIf { it.isNotBlank() }?.let { artist ->
                        { onOpenArtist(artist, item.release.artistId.takeIf(String::isNotBlank)) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
