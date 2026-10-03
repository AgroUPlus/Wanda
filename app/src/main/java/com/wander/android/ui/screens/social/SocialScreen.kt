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
import com.wander.android.core.permissions.rememberLocalNetworkGate
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
    // Asked at the tap that needs it, as on a profile: without local-network access the peer tier
    // fails its probe and every track takes the relay. Joining goes ahead either way.
    val localNetworkGate = rememberLocalNetworkGate()

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

            item(key = "me") {
                MyProfileCard(
                    myUsername = state.myUsername,
                    myAvatarUrl = state.myAvatarUrl,
                    friendCount = state.friends.size,
                    onOpenMyProfile = onOpenMyProfile,
                    onShowCode = {
                        searching = true
                        if (!search.showingCode) viewModel.toggleFriendCode()
                    }
                )
            }

            item(key = "jam") {
                JamCard(
                    jam = jam,
                    people = jam?.members?.filterNot { it.equals(state.myUsername, ignoreCase = true) }
                        ?: state.friends.map { it.username },
                    onOpenJam = onOpenJam
                )
            }

            val playing = state.friends.mapNotNull { profile ->
                state.playing(profile.username)?.let { profile to it }
            }
            if (playing.isNotEmpty()) {
                item(key = "listening_header") { ListeningNowHeader(liveCount = playing.size) }
                item(key = "listening_carousel") {
                    ListeningNowCarousel(playing = playing, onOpenProfile = onOpenProfile)
                }
            }

            requestSection(
                key = "incoming",
                title = R.string.social_wants_to_be_friends,
                profiles = state.incoming,
                actionLabel = R.string.common_accept,
                onAction = viewModel::accept,
                onOpenProfile = onOpenProfile
            )

            if (state.friends.isNotEmpty()) {
                allFriendsSection(
                    friends = state.friends,
                    playing = state::playing,
                    isListeningAlong = { state.session?.host.equals(it, ignoreCase = true) },
                    onOpenProfile = onOpenProfile,
                    onJoin = { host -> localNetworkGate { viewModel.startListenAlong(host) } }
                )
            }

            requestSection(
                key = "outgoing",
                title = R.string.social_waiting_for_answer,
                profiles = state.outgoing,
                actionLabel = R.string.common_cancel,
                onAction = viewModel::remove,
                onOpenProfile = onOpenProfile
            )

            activityRow(unread = unread, onOpenActivity = onOpenActivity)

            if (state.feed.isNotEmpty()) {
                item(key = "feed_header") { SectionTitle(stringResource(R.string.social_lately)) }
                items(
                    count = state.feed.size,
                    key = { index -> "feed_" + index }
                ) { index ->
                    FeedItemCard(
                        item = state.feed[index],
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (state.feedLoadingMore) {
                    items(
                        count = FEED_SKELETON_ROWS,
                        key = { index -> "feed_skeleton_" + index }
                    ) {
                        FeedItemSkeleton(modifier = Modifier.padding(horizontal = 16.dp))
                    }
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
