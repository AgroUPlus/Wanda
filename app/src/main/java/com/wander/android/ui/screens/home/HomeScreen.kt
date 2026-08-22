package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.agro.AgroSessionViewModel
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.SessionSheet
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The other devices' session, shown at the top of Home the way Spotify keeps "listening on…"
    // in reach. Independent of the one-shot resume card, so it is available whenever you look.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()
    val session by agroViewModel.latestSession.collectAsStateWithLifecycle()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    val sessionArtwork by agroViewModel.sessionArtwork.collectAsStateWithLifecycle()
    val isResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    var showSessionSheet by rememberSaveable { mutableStateOf(false) }

    val sessionDevice = remember(session, agroDevices) {
        session?.let { handoff -> agroDevices.firstOrNull { it.deviceId == handoff.deviceId } }
    }
    // The device's own online state decides this, not the session's `isPlaying` flag: that flag is
    // whatever the sender last wrote, so a client that exited mid-track left it saying "playing"
    // forever. `isPlaying` only stands in when the sending device is not in the list at all.
    val sessionIsLive = sessionDevice?.isOnline
        ?: (session?.isPlaying == true)

    // The session can disappear while the sheet is open — it was resumed here, or the other device
    // stopped — and a sheet describing nothing should not stay up.
    if (showSessionSheet && session == null) showSessionSheet = false

    session?.let { handoff ->
        if (showSessionSheet) {
            SessionSheet(
                handoff = handoff,
                deviceName = sessionDevice?.petname ?: "another device",
                isLive = sessionIsLive,
                isResuming = isResuming,
                artworkUrl = sessionArtwork,
                onResume = {
                    agroViewModel.resume(handoff)
                    showSessionSheet = false
                },
                onDismiss = { showSessionSheet = false }
            )
        }
    }

    // Held above the LazyColumn, keyed by shelf. `rememberLazyListState()` called inside a lazy
    // `item {}` is disposed the moment that shelf scrolls off, so it neither preserved the
    // horizontal position nor avoided reallocating the state on the way back.
    val carouselStates = remember { mutableMapOf<String, LazyListState>() }

    // The same long-press menu Library and Search use, so a track offers the same actions
    // wherever it is shown. Held here rather than per shelf: only one can be open at a time.
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onStartRadio = { viewModel.startRadio(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onRemove = null,
            onDismiss = { actionsFor = null },
            onShare = if (viewModel.canShare(track)) {
                { viewModel.share(track) }
            } else {
                null
            },
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else {
                null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

            state.isEmpty -> EmptyState(
                title = "Nothing to play yet",
                message = "Connect Navidrome or YouTube Music in Settings, or grant access to " +
                    "music stored on this device.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
                modifier = Modifier.align(Alignment.Center)
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::pullToRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header", contentType = "header") {
                        HomeHeader(
                            greeting = state.greeting,
                            hasSession = session != null,
                            onOpenSessions = { showSessionSheet = true },
                            onOpenStats = onOpenStats,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    state.sections.forEach { section ->
                        homeSection(section, viewModel, carouselStates) { actionsFor = it }
                    }
                }
            }
        }
    }
}

/**
 * One shelf. Split out of the screen body so a new shelf costs a [HomeSection] and nothing else.
 */
private fun LazyListScope.homeSection(
    section: HomeSection,
    viewModel: HomeViewModel,
    carouselStates: MutableMap<String, LazyListState>,
    onLongPress: (UnifiedTrack) -> Unit
) {
    item(key = "${section.id}-title", contentType = "section-title") {
        SectionTitle(section.title)
    }

    when (section.style) {
        HomeSectionStyle.MIX_CAROUSEL -> item(
            key = "${section.id}-row",
            contentType = "mix-carousel"
        ) {
            LazyRow(
                state = carouselStates.getOrPut(section.id) { LazyListState() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                itemsIndexed(
                    items = section.mixes,
                    key = { _, mix -> "${section.id}-${mix.id}" },
                    contentType = { _, _ -> "mix-card" }
                ) { _, mix ->
                    SmartMixCard(mix = mix, onPlay = { viewModel.playMix(mix) })
                }
            }
        }

        HomeSectionStyle.TRACK_CAROUSEL -> item(
            key = "${section.id}-row",
            contentType = "carousel"
        ) {
            LazyRow(
                state = carouselStates.getOrPut(section.id) { LazyListState() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                // Indexed, so playing a card does not linear-scan the shelf for the track and
                // does not capture the whole list in a lambda that changes every recomposition.
                itemsIndexed(
                    items = section.tracks,
                    key = { _, track -> "${section.id}-${track.id}" },
                    contentType = { _, _ -> "track-card" }
                ) { index, track ->
                    HorizontalTrackCard(
                        track = track,
                        onPlay = { viewModel.play(section.tracks, index) },
                        onLongPress = { onLongPress(track) }
                    )
                }
            }
        }

        HomeSectionStyle.TRACK_LIST -> itemsIndexed(
            items = section.tracks,
            key = { _, track -> "${section.id}-${track.id}" },
            contentType = { _, _ -> "track-row" }
        ) { index, track ->
            TrackRow(
                track = track,
                onPlay = { viewModel.play(section.tracks, index) },
                onToggleLike = { viewModel.toggleLike(track) },
                onLongPress = { onLongPress(track) }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    hasSession: Boolean,
    onOpenSessions: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp)
    ) {
        // The greeting is the header. The app's own name told the user nothing they didn't
        // already know from having opened it.
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        // Only present when there is somewhere to hand off from, so the header stays quiet the
        // rest of the time. No live badge: the icon's presence already says a session exists, and
        // the sheet behind it is where "still playing" actually means something.
        if (hasSession) {
            IconButton(onClick = onOpenSessions) {
                Icon(Icons.Rounded.Devices, contentDescription = "Sessions on other devices")
            }
        }
        IconButton(onClick = onOpenStats) {
            Icon(Icons.Rounded.BarChart, contentDescription = "Listening statistics")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}
