package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow

@Composable
internal fun LocalQueueContent(
    state: PlaybackState,
    playerConnection: PlayerConnection,
    onTrackLongPress: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit
) {
    if (state.queue.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.queue_queue_empty),
            message = stringResource(R.string.queue_play_something_will_show_up),
            modifier = Modifier.padding(top = 96.dp)
        )
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = state.queue,
                key = { index, track -> "$index-${track.id}" },
                contentType = { _, _ -> "track" }
            ) { index, track ->
                TrackRow(
                    track = track,
                    isPlaying = index == state.currentIndex,
                    onPlay = { playerConnection.seekToIndex(index) },
                    onToggleLike = { onToggleLike(track) },
                    onLongPress = { onTrackLongPress(track) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
