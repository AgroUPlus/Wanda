package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow

/**
 * The local queue as one plain list, in play order. Rows are keyed by [QueueEntry.key], which
 * follows a track through moves and inserts rather than being tied to its position.
 */
@Composable
internal fun LocalQueueContent(
    entries: List<QueueEntry>,
    playerConnection: PlayerConnection,
    onTrackLongPress: (QueueEntry) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit
) {
    if (entries.isEmpty()) {
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
            items(
                items = entries,
                key = { entry -> entry.key },
                contentType = { "track" }
            ) { entry ->
                TrackRow(
                    track = entry.track,
                    isPlaying = entry.role == QueueItemRole.CURRENT,
                    onPlay = { playerConnection.seekToIndex(entry.queueIndex) },
                    onToggleLike = { onToggleLike(entry.track) },
                    onLongPress = { onTrackLongPress(entry) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
