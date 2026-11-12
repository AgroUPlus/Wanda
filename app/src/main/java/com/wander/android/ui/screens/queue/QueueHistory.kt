package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.groupedItemShape
import com.wander.android.ui.components.rememberRubberBandOverscroll

/**
 * The History tab: what has already played in this queue, most recent first — the track just
 * finished is the one you are likeliest to want back. Each row plays again from its trailing
 * button as well as from a tap, and still swipes away like an upcoming one.
 *
 * It used to be a section folded shut under the upcoming tracks, one more thing to open in the
 * middle of a list that is about what comes next.
 */
@Composable
internal fun QueueHistory(
    played: List<QueueEntry>,
    onPlay: (Int) -> Unit,
    onLongPress: (QueueEntry) -> Unit,
    onRemove: (QueueEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (played.isEmpty()) {
        Text(
            text = stringResource(R.string.queue_next_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
        )
        return
    }
    val recentFirst = played.asReversed()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        // The same split with the drawer as the Up next tab: see `QueueUpNext`.
        overscrollEffect = rememberRubberBandOverscroll(),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            recentFirst,
            key = { _, entry -> entry.key },
            contentType = { _, _ -> "queue-track" }
        ) { index, entry ->
            val shape = groupedItemShape(index, recentFirst.size)
            QueueSwipeToRemove(enabled = true, shape = shape, onRemove = { onRemove(entry) }) {
                QueueTrackTile(
                    entry = entry,
                    shape = shape,
                    isDragging = false,
                    onPlay = { onPlay(entry.queueIndex) },
                    onLongPress = { onLongPress(entry) },
                    onPlayAgain = { onPlay(entry.queueIndex) }
                )
            }
        }
    }
}
