package com.wander.android.ui.screens.queue

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection

/**
 * What the queue drawer holds: the playing track's cover strip, then the Up next and History tabs
 * and the list of whichever is picked. The host, [QueueDrawer], owns the gestures, the long-press
 * sheet and the undo snackbar; everything here is laid out on its surface.
 */
@Composable
internal fun QueueDrawerBody(
    state: PlaybackState,
    entries: List<QueueEntry>,
    canReorder: Boolean,
    playerConnection: PlayerConnection,
    surfaceColor: Color,
    coverHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (QueueEntry) -> Unit,
    onRemove: (QueueEntry) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(QueueTab.UP_NEXT) }
    val current = entries.firstOrNull { it.role == QueueItemRole.CURRENT }
    val upcoming = remember(entries) { entries.filter { it.role == QueueItemRole.UP_NEXT } }
    val played = remember(entries) { entries.filter { it.role == QueueItemRole.PREVIOUS } }

    Column(modifier = Modifier.fillMaxSize()) {
        QueueCoverStrip(
            track = current?.track ?: state.currentTrack,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            onTogglePlay = { playerConnection.togglePlayPause() },
            onLongPressTrack = current?.let { now -> { onLongPress(now) } },
            surfaceColor = surfaceColor,
            height = coverHeight,
            menu = {
                if (state.queue.isNotEmpty()) {
                    QueueMoreMenu(
                        queue = state.queue,
                        canClear = !state.orderLocked,
                        onClearQueue = { playerConnection.clearQueue() }
                    )
                }
            }
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            QueueTabsHeader(
                selected = tab,
                onSelect = { tab = it },
                meta = {
                    when (tab) {
                        QueueTab.UP_NEXT -> UpNextMeta(playerConnection, state, upcoming)
                        QueueTab.HISTORY -> HistoryMeta(played.size)
                    }
                }
            )
            Crossfade(
                targetState = tab,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "queueTab",
                modifier = Modifier.fillMaxSize()
            ) { shown ->
                QueueTabList(
                    shown = shown,
                    state = state,
                    entries = entries,
                    played = played,
                    currentEntry = current,
                    canReorder = canReorder,
                    playerConnection = playerConnection,
                    onMove = onMove,
                    onLongPress = onLongPress,
                    onRemove = onRemove
                )
            }
        }
    }
}

/** The list under the tabs: what plays next, ending on the radio row, or what has played. */
@Composable
private fun QueueTabList(
    shown: QueueTab,
    state: PlaybackState,
    entries: List<QueueEntry>,
    played: List<QueueEntry>,
    currentEntry: QueueEntry?,
    canReorder: Boolean,
    playerConnection: PlayerConnection,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (QueueEntry) -> Unit,
    onRemove: (QueueEntry) -> Unit
) {
    val onPlay: (Int) -> Unit = { playerConnection.seekToIndex(it) }
    when (shown) {
        QueueTab.UP_NEXT -> QueueUpNext(
            entries = entries,
            canReorder = canReorder,
            onPlay = onPlay,
            onMove = onMove,
            onLongPress = onLongPress,
            onRemove = onRemove,
            // Something else decides what follows a jam or a listen-along.
            footer = if (state.orderLocked) {
                null
            } else {
                {
                    QueueRadioRow(
                        isRadioMode = state.isRadioMode,
                        artist = (currentEntry?.track ?: state.currentTrack)?.artist,
                        onRadioModeChange = { playerConnection.setRadioMode(it) }
                    )
                }
            }
        )
        QueueTab.HISTORY -> QueueHistory(
            played = played,
            onPlay = onPlay,
            onLongPress = onLongPress,
            onRemove = onRemove
        )
    }
}
