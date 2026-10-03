package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.permissions.rememberLocalNetworkGate
import com.wander.android.ui.components.CompactHeroBarHeight
import com.wander.android.ui.components.CompactHeroTopBar
import com.wander.android.ui.components.RankedList
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.components.rememberCollapsingTitleState
import com.wander.android.ui.theme.profileName

/**
 * One person: who they are, what they are playing, and how much you two overlap.
 *
 * Where a surface is closed the screen says so plainly. An empty chart and a chart someone chose
 * not to share look identical, and only one of them is worth explaining.
 */
@Composable
internal fun ProfileScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenJam: () -> Unit,
    onOpenActivity: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Asked here, at the tap that needs it. Without local-network access Android silently refuses
    // both halves of the peer tier — this device cannot open a connection to the host, and its own
    // server cannot accept one — so the LAN tier fails its probe and every track takes the relay.
    // The session starts either way: a refusal costs speed, not the feature.
    val startListenAlong = rememberLocalNetworkGate(viewModel::startListenAlong)

    val listState = rememberLazyListState()
    val titleState = rememberCollapsingTitleState(listState)
    val topInset = contentPadding.calculateTopPadding()

    // One Box, like the artist page: the list runs under the bar, which turns opaque and takes the
    // name once the hero's own copy of it scrolls up to meet it.
    Box(modifier = Modifier.fillMaxSize()) {
        ProfileBackdrop(username = state.username, modifier = Modifier.align(Alignment.TopEnd))

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topInset + CompactHeroBarHeight,
                bottom = contentPadding.calculateBottomPadding()
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            val profile = state.profile
            if (profile == null) {
                if (state.isLoading) {
                    item(key = "loading") { ProfileSkeleton() }
                } else {
                    item(key = "missing") {
                        Text(
                            // Not found and not visible are the same answer from the server, on
                            // purpose, so this cannot become a way to test whether an account exists.
                            text = state.error ?: stringResource(R.string.social_no_such_account),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            item(key = "header") {
                ProfileHero(profile, titleModifier = Modifier.collapsingTitleSource(titleState))
            }

            item(key = "action") {
                ProfileActions(
                    profile = profile,
                    onInviteToJam = onOpenJam,
                    onOpenActivity = onOpenActivity,
                    onAddFriend = viewModel::sendRequest,
                    onAccept = viewModel::accept,
                    onDecline = viewModel::remove
                )
            }

            item(key = "listening") {
                ProfileListeningCard(
                    profile = profile,
                    now = state.nowPlaying,
                    isListeningAlong = state.isListeningAlong,
                    onListenAlong = startListenAlong,
                    onStopListeningAlong = viewModel::stopListenAlong
                )
            }

            // Their own listening, not just the overlap with yours: "what does this person
            // actually listen to" is the thing a profile is for.
            val friendStats = state.stats
            if (friendStats != null) {
                item(key = "their-stats") {
                    Column {
                        SectionTitle(stringResource(R.string.social_their_listening, profile.name), top = 32.dp)
                        StatTiles(plays = friendStats.playCount, hours = friendStats.secondsTotal / 3600)
                    }
                }
                if (friendStats.topArtists.isNotEmpty()) {
                    item(key = "their-artists") {
                        Column {
                            SectionTitle(stringResource(R.string.social_top_artists), top = 32.dp)
                            RankedList(friendStats.topArtists.take(TOP_COUNT), countLabel = { stringResource(R.string.social_plays_2, it.toString()) })
                        }
                    }
                }
                if (friendStats.topTracks.isNotEmpty()) {
                    item(key = "their-tracks") {
                        Column {
                            SectionTitle(stringResource(R.string.social_top_tracks), top = 32.dp)
                            RankedList(friendStats.topTracks.take(TOP_COUNT), countLabel = { stringResource(R.string.social_plays_2, it.toString()) })
                        }
                    }
                }
            }

            tasteMatchSection(profile, state.tasteMatch)
        }

        CompactHeroTopBar(
            titleState = titleState,
            onBack = onBack,
            title = state.profile?.name.orEmpty(),
            heroTitleStyle = MaterialTheme.typography.profileName,
            heroTitleMaxLines = ProfileNameMaxLines,
            topInset = topInset,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            state.profile?.let { ProfileOverflow(it, onRemove = viewModel::remove, onBlock = viewModel::block) }
        }
    }
}

private const val TOP_COUNT = 5
