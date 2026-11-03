package com.wander.android.ui

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.player.DockedDismiss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The docked player swiped away: paused and out of sight until something plays again.
 *
 * Pause, not stop. The queue and position stay exactly where they were, so playing again from the
 * notification, a headset button or any screen simply carries on — and Media3's session service
 * already drops out of the foreground on a pause, which leaves its notification dismissable without
 * this app tearing the session down. Stopping would have thrown the queue away to save a
 * notification the user can swipe off themselves.
 *
 * The dismissal clears on the next start of playback from anywhere — the paused-to-playing edge of
 * [PlaybackState.isPlaying], not its level, since the player is still reporting "playing" in the
 * instant the swipe commits.
 */
@Stable
internal class MiniPlayerDismissal(
    private val dismissedState: MutableState<Boolean>,
    private val playerConnection: PlayerConnection,
    private val playbackState: State<PlaybackState>,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context,
    private val scope: CoroutineScope
) {
    val isDismissed: Boolean get() = dismissedState.value

    private var undoJob: Job? = null

    val gesture = DockedDismiss(
        isAllowed = { !playerConnection.isFollowing },
        onDismiss = ::dismiss
    )

    private fun dismiss() {
        val wasPlaying = playbackState.value.isPlaying
        playerConnection.pause()
        dismissedState.value = true
        undoJob?.cancel()
        undoJob = scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.player_dismissed),
                actionLabel = context.getString(R.string.common_undo),
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                dismissedState.value = false
                if (wasPlaying) playerConnection.resume()
            }
        }
    }

    /** Playback started again by any route: the strip returns, and the undo has nothing left to do. */
    fun onPlaybackStarted() {
        if (!dismissedState.value) return
        dismissedState.value = false
        undoJob?.cancel()
        undoJob = null
    }
}

@Composable
internal fun rememberMiniPlayerDismissal(
    playerConnection: PlayerConnection,
    playbackState: State<PlaybackState>,
    snackbarHostState: SnackbarHostState
): MiniPlayerDismissal {
    val dismissedState = rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dismissal = remember(playerConnection, snackbarHostState, context) {
        MiniPlayerDismissal(
            dismissedState = dismissedState,
            playerConnection = playerConnection,
            playbackState = playbackState,
            snackbarHostState = snackbarHostState,
            context = context,
            scope = scope
        )
    }
    LaunchedEffect(dismissal) {
        var wasPlaying = playbackState.value.isPlaying
        snapshotFlow { playbackState.value.isPlaying }
            .distinctUntilChanged()
            .collect { playing ->
                if (playing && !wasPlaying) dismissal.onPlaybackStarted()
                wasPlaying = playing
            }
    }
    return dismissal
}
