package com.wander.android.ui.screens.queue
 
import kotlin.coroutines.cancellation.CancellationException
import com.wander.android.ui.components.backResistance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.activity.compose.PredictiveBackHandler
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.scrollingTitle
import kotlinx.coroutines.launch

/**
 * The queue, as a drawer over the player rather than a screen on top of it — a panel that follows
 * the finger ([QueueDrawerState]) rather than a sheet that can only be shown or hidden.
 *
 * It used to be a full navigation destination, which meant looking at what was coming next replaced
 * the thing you were listening to. A sheet keeps the player behind it and puts the running order in
 * reach of a thumb, which is what it is for. It opens from the button in the player's top bar or by
 * dragging upwards anywhere on the player — see `swipeUpToOpenQueue`.
 *
 * Only the local queue. A jam's order is voted on rather than dragged, and that screen is
 * substantially its own thing — the caller sends a jam to `QueueScreen` instead; see
 * `PlayerSheetContent`.
 */

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
internal fun QueueDrawer(
    drawer: QueueDrawerState,
    playerConnection: PlayerConnection,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    queueViewModel: QueueViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }
    val addToPlaylist = AddToPlaylistHost()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // The snackbar is raised from inside a coroutine, which is not a composition — so its text
    // comes from the Context rather than from stringResource().
    val context = LocalContext.current

    // The drawer's own, not the app's. A `ModalBottomSheet` is its own window drawn over
    // everything, so a snackbar raised at the shell would come up *behind* the queue — which, for
    // the one message in the app offering to undo something, is the same as not showing it.
    val snackbarHostState = remember { SnackbarHostState() }
    var itemGenerations by remember { mutableStateOf(mapOf<String, Int>()) }

    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val closeDrawer: () -> Unit = { scope.launch { drawer.close(spec) } }

    // A back swipe pulls the drawer down with the finger, with growing resistance, and lets it
    // spring back up if the swipe is abandoned.
    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { drawer.snapTo(1f - backResistance(it.progress) * BackPeek) }
            drawer.close(spec)
        } catch (_: CancellationException) {
            drawer.open(spec)
        }
    }

    // Keyed on the drawer alone: a connection swapped out mid-drag (the spec is a fresh object on
    // every recomposition) drops the gesture it was carrying.
    val currentSpec by rememberUpdatedState(spec)
    val nestedScroll = remember(drawer) { drawerNestedScroll(drawer) { currentSpec } }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Tapping what is left of the player puts the drawer away; it also stops those taps from
        // reaching the player's controls while the drawer is over them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = drawer.progress.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                .pointerInput(Unit) { detectTapGestures { closeDrawer() } }
        )

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(LocalDensity.current) { drawer.heightPx.toDp() })
                .graphicsLayer {
                    translationY = (1f - drawer.progress.coerceIn(0f, 1f)) * size.height
                }
                .nestedScroll(nestedScroll)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy -> drawer.dragBy(dy) },
                    onDragStopped = { velocity -> drawer.settle(velocity, spec) }
                )
        ) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                    QueueDrawerHeader(
                        currentIndex = state.currentIndex,
                        queueSize = state.queue.size,
                        orderLocked = state.orderLocked,
                        onClearQueue = playerConnection::clearQueue
                    )


                    QueueUpNext(
                        entries = rememberQueueEntries(state.queue, state.currentIndex, itemGenerations),
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
                                    message = context.getString(
                                        R.string.queue_removed,
                                        entry.track.title
                                    ),
                                    actionLabel = context.getString(R.string.common_undo),
                                    withDismissAction = true
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    itemGenerations = itemGenerations + (entry.track.id to (itemGenerations[entry.track.id] ?: 0) + 1)
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
}

