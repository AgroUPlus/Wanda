package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.headerInset
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
    onOpenInbox: () -> Unit = {},
    onOpenCircle: () -> Unit = {},
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
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Friends", style = MaterialTheme.typography.headlineLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isPaired) {
                    InboxAction(unread = unread, onClick = onOpenInbox)
                    FilledTonalIconButton(onClick = { searching = true }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Find people")
                    }
                }
                // Outside the `isPaired` branch on purpose. Your own profile — and the listening
                // statistics behind it — is about you, not about anyone else, so it must not
                // disappear because no server is paired or because nobody has been added yet.
                // This is also where the stats button lives now: it was in Home's header, which
                // is the one place on the screen that has nothing to do with statistics.
                FilledTonalIconButton(onClick = onOpenMyProfile) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = "My profile")
                }
            }
        }

        if (!state.isPaired) {
            NotPairedNotice(onOpenSettings = onOpenSettings)
            return
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            state.error?.let { message ->
                item(key = "error") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (state.friends.isNotEmpty()) {
                item(key = "friend_grid") {
                    FriendGrid(
                        friends = state.friends,
                        listening = remember(state.nowPlaying) {
                            state.nowPlaying.map { it.username.lowercase() }.toSet()
                        },
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            item(key = "destinations") {
                SocialTiles(
                    jamSubtitle = jam?.let { "Jam · ${it.code}" },
                    circleSubtitle = "Recap & activity",
                    onOpenJam = onOpenJam,
                    onOpenCircle = onOpenCircle,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val playing = state.friends.mapNotNull { profile ->
                state.playing(profile.username)?.let { profile to it }
            }
            if (playing.isNotEmpty()) {
                item(key = "listening_header") { SectionHeader("Listening now") }
                item(key = "listening_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(
                            count = playing.size,
                            key = { index -> "presence_" + playing[index].first.username }
                        ) { index ->
                            val (profile, now) = playing[index]
                            FriendPresenceCard(
                                profile = profile,
                                nowPlaying = now,
                                isListeningAlong = state.session?.host
                                    .equals(profile.username, ignoreCase = true),
                                onOpenProfile = { onOpenProfile(profile.username) }
                            )
                        }
                    }
                }
            }

            // The feed the server has always answered and nothing on this screen ever asked for.
            // Placed above the roster because it is the only part of the tab that changes.
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

            if (state.friends.isNotEmpty()) {
                item(key = "friends_header") { SectionHeader("Friends") }
                items(state.friends, key = { "friend_" + it.username }) { profile ->
                    FriendRow(
                        profile = profile,
                        subtitle = friendSubtitle(profile, state.playing(profile.username)),
                        actionLabel = null,
                        onAction = null,
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
                        text = "Nobody yet. Tap the add button to find people by username — you " +
                            "will only turn up in their search if you have made yourself " +
                            "discoverable in Settings → Privacy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
internal fun NotPairedNotice(onOpenSettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(20.dp)
    ) {
        Text(
            text = "Friends need an Agro server.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Pair with one — or create an account on one — and you can see what the people " +
                "you know are listening to, and listen along with them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.material3.FilledTonalButton(onClick = onOpenSettings) {
            Text("Open Settings")
        }
    }
}
