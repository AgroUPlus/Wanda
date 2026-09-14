package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.scrollingTitle
import kotlinx.coroutines.launch

/**
 * The queue, as a drawer over the player rather than a screen on top of it.
 *
 * It used to be a full navigation destination, which meant looking at what was coming next replaced
 * the thing you were listening to. A sheet keeps the player behind it and puts the running order in
 * reach of a thumb, which is what it is for. It opens from the button in the player's top bar or by
 * dragging the handle at its foot — see `QueuePullTab`.
 *
 * Only the local queue. A jam's order is voted on rather than dragged, and that screen is
 * substantially its own thing — the caller sends a jam to `QueueScreen` instead; see
 * `PlayerSheetContent`.
 */
@Composable
internal fun QueueDrawer(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    queueViewModel: QueueViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    val addToPlaylist = AddToPlaylistHost()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // The drawer's own, not the app's. A `ModalBottomSheet` is its own window drawn over
    // everything, so a snackbar raised at the shell would come up *behind* the queue — which, for
    // the one message in the app offering to undo something, is the same as not showing it.
    val snackbarHostState = remember { SnackbarHostState() }

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = {
                queueViewModel.playNext(track)
                actionsFor = null
            },
            onAddToQueue = {
                queueViewModel.addToQueue(track)
                actionsFor = null
            },
            onStartRadio = {
                queueViewModel.startRadio(track)
                actionsFor = null
            },
            onToggleLike = { queueViewModel.toggleLike(track) },
            onRemove = {
                val idx = state.queue.indexOfFirst { it.id == track.id }
                if (idx >= 0) queueViewModel.removeFromQueue(idx)
                actionsFor = null
            },
            onOpenArtist = track.artist
                .takeIf { it.isNotBlank() }
                ?.let { artist -> onOpenArtist?.let { open -> { open(artist, track.artistId) } } },
            onDismiss = { actionsFor = null },
            onShare = if (queueViewModel.canShare(track)) {
                { queueViewModel.share(track) }
            } else null,
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else null
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SheetHeightFraction)
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.currentTrack?.let { current ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                        )
                        Text(
                            text = current.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                        )
                    }
                }

                Text(
                    text = "Up next",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
                )

                QueueUpNext(
                    entries = rememberQueueEntries(state.queue, state.currentIndex),
                    canReorder = !state.orderLocked,
                    onPlay = { playerConnection.seekToIndex(it) },
                    onMove = { from, to ->
                        playerConnection.moveInQueue(from, to)
                        haptics.settled()
                    },
                    onLongPress = { actionsFor = it },
                    onRemove = { entry ->
                        queueViewModel.removeFromQueue(entry.queueIndex)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Removed ${entry.track.title}",
                                actionLabel = "Undo",
                                withDismissAction = true
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                queueViewModel.insertInQueue(entry.queueIndex, entry.track)
                            }
                        }
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** Tall enough to be the queue, short enough that the player is still visibly behind it. */
private const val SheetHeightFraction = 0.82f
