package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackRow

@Composable
fun QueueScreen(
    playerConnection: PlayerConnection,
    onClose: () -> Unit
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close queue")
            }
            Text(
                text = "Up next",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            if (state.queue.isNotEmpty()) {
                IconButton(onClick = playerConnection::clearQueue) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear queue")
                }
            }
        }

        if (state.queue.isEmpty()) {
            EmptyState(
                title = "The queue is empty",
                message = "Play something and it will show up here.",
                modifier = Modifier.padding(top = 96.dp)
            )
            return@Column
        }

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
                    onRemove = { playerConnection.removeFromQueue(index) }
                )
            }
        }
    }
}
