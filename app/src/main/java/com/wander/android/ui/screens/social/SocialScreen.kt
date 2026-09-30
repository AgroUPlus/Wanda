package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.listInset

/**
 * Who you follow, who is asking to follow you, and what everyone is playing.
 *
 * The presence row comes first because it is the only part that changes minute to minute — the
 * friend list below it is the same list it was yesterday, and burying live information under a
 * static roster gets the priority backwards.
 */
@Composable
internal fun SocialScreen(
    contentPadding: PaddingValues,
    onOpenProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenJam: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenOffGrid: () -> Unit = {},
    onOpenMyProfile: () -> Unit = {},
    viewModel: SocialViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val jamViewModel: JamViewModel = hiltViewModel()
    val jam = jamViewModel.state.collectAsStateWithLifecycle().value.jam
    // The badge reads the same cached count the Inbox screen does, so the two cannot disagree.
    val unread = hiltViewModel<InboxViewModel>().state.collectAsStateWithLifecycle().value.unread
    var searching by remember { mutableStateOf(false) }

    if (searching) {
        UserSearchSheet(
            state = search,
            onQueryChange = viewModel::onQueryChange,
            onSendRequest = viewModel::sendRequest,
            onOpenProfile = onOpenProfile,
            onToggleCode = viewModel::toggleFriendCode,
            onRefreshCode = viewModel::refreshFriendCode,
            onRevokeCode = viewModel::revokeFriendCode,
            onDismiss = {
                searching = false
                viewModel.clearSearch()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SocialHeader(
            state = state,
            contentPadding = contentPadding,
            onOpenOffGrid = onOpenOffGrid,
            onFindPeople = { searching = true }
        )

        if (!state.isPaired) {
            NotPairedNotice(onOpenSettings = onOpenSettings)
            return
        }

        // Placeholders only on the very first read, and only with nothing cached. After that Room
        // answers instantly and the tab would flash placeholders over content already on screen.
        if (state.loading && state.isEmpty) {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding.listInset())) {
                repeat(SKELETON_ROWS) { SkeletonRow() }
            }
            return
        }

        val listState = rememberLazyListState()

        // Asking near the end rather than at it, so the next page is usually already there by the
        // time the last card is reached.
        val wantsMore by remember(state.feed.size, state.feedExhausted) {
            derivedStateOf {
                if (state.feed.isEmpty() || state.feedExhausted) return@derivedStateOf false
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    ?: return@derivedStateOf false
                last.index >= listState.layoutInfo.totalItemsCount - FEED_PREFETCH_DISTANCE
            }
        }
        LaunchedEffect(wantsMore) {
            if (wantsMore) viewModel.loadMoreFeed()
        }

        LazyColumn(
            state = listState,
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            state.error?.let { message ->
                item(key = "error") {
                    SocialErrorCard(message = message)
                }
            }

            item(key = "hero") {
                FriendsHero(
                    friends = state.friends,
                    listeningNow = state.nowPlaying.size,
                    myUsername = state.myUsername,
                    myAvatarUrl = state.myAvatarUrl,
                    onOpenMyProfile = onOpenMyProfile
                )
            }

            if (state.friends.isNotEmpty()) {
                item(key = "friend_grid") {
                    FriendGrid(
                        friends = state.friends,
                        listening = remember(state.nowPlaying) {
                            state.nowPlaying.map { it.username.lowercase() }.toSet()
                        },
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)
                    )
                }
            }

            item(key = "destinations") {
                SocialTiles(
                    jamSubtitle = jam?.let { "Jam · ${it.code}" },
                    activitySubtitle = if (unread > 0) "$unread unread" else "Circle & shared songs",
                    onOpenJam = onOpenJam,
                    onOpenActivity = onOpenActivity,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val playing = state.friends.mapNotNull { profile ->
                state.playing(profile.username)?.let { profile to it }
            }
            if (playing.isNotEmpty()) {
                item(key = "listening_header") { SectionHeader("Listening now") }
                item(key = "listening_row") {
                    ListeningNowRow(
                        playing = playing,
                        isListeningAlong = { state.session?.host.equals(it, ignoreCase = true) },
                        onOpenProfile = onOpenProfile
                    )
                }
            }

            if (state.feed.isNotEmpty()) {
                item(key = "feed_header") { SectionHeader("Lately") }
                items(
                    count = state.feed.size,
                    key = { index -> "feed_" + index }
                ) { index ->
                    FeedItemCard(
                        item = state.feed[index],
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                if (state.feedLoadingMore) {
                    items(
                        count = FEED_SKELETON_ROWS,
                        key = { index -> "feed_skeleton_" + index }
                    ) {
                        FeedItemSkeleton(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }

            if (state.incoming.isNotEmpty()) {
                item(key = "incoming_header") { SectionHeader("Wants to be friends") }
                items(state.incoming, key = { "incoming_" + it.username }) { profile ->
                    FriendRow(
                        profile = profile,
                        subtitle = "@" + profile.username,
                        actionLabel = "Accept",
                        onAction = { viewModel.accept(profile.username) },
                        onClick = { onOpenProfile(profile.username) }
                    )
                }
            }

            if (state.outgoing.isNotEmpty()) {
                item(key = "outgoing_header") { SectionHeader("Waiting for an answer") }
                items(state.outgoing, key = { "outgoing_" + it.username }) { profile ->
                    FriendRow(
                        profile = profile,
                        subtitle = "@" + profile.username,
                        actionLabel = "Cancel",
                        onAction = { viewModel.remove(profile.username) },
                        onClick = { onOpenProfile(profile.username) }
                    )
                }
            }

            if (state.isEmpty && !state.isRefreshing) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.social_nobody_yet_tap_add_button),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            }
        }
    }
}

/** Enough to fill the fold. A placeholder nobody scrolls to is work for nothing. */
private const val SKELETON_ROWS = 6

/**
 * How close to the end the list gets before the next page is asked for.
 */
private const val FEED_PREFETCH_DISTANCE = 4
