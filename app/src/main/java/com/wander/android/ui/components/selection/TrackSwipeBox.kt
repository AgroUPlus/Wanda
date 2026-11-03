package com.wander.android.ui.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.ElasticSwipeBox
import com.wander.android.ui.components.SwipeSide
import com.wander.android.ui.components.isPlayableNow

/**
 * A track row's swipe shortcuts: toward the end (right, in left-to-right) plays it next, toward
 * the start adds it to the end of the queue. Each ticks as it passes the trigger and confirms with
 * a snackbar that offers to undo it, since the row itself springs back unchanged.
 *
 * Off while [enabled] is false — during a selection a swipe would be a second gesture on a row
 * whose tap already means something else — and for a track that would not play right now.
 * Not for the queue, whose own swipe removes the row, nor for a list inside a horizontal pager,
 * where a sideways drag already means "next page".
 */
@Composable
fun TrackSwipeBox(
    track: UnifiedTrack,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val actions: TrackSelectionViewModel = hiltViewModel()
    val playingNext = stringResource(R.string.swipe_playing_next, track.title)
    val queued = stringResource(R.string.swipe_added_to_queue, track.title)
    val active = enabled && track.isPlayableNow()
    ElasticSwipeBox(
        onSwipeStart = { actions.playNext(listOf(track), playingNext) }.takeIf { active },
        onSwipeEnd = { actions.addToQueue(listOf(track), queued) }.takeIf { active },
        enabled = active,
        modifier = modifier,
        background = { side, progress -> SwipeHint(side, progress) },
        content = content
    )
}

/** What the pull uncovers: the action's icon on a tonal ground, growing into place. */
@Composable
private fun SwipeHint(side: SwipeSide?, progress: Float) {
    if (side == null) return
    val playNext = side == SwipeSide.START
    Box(
        contentAlignment = if (playNext) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = progress))
            .padding(horizontal = 24.dp)
    ) {
        Icon(
            imageVector = if (playNext) Icons.AutoMirrored.Rounded.PlaylistAdd else Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    val scale = 0.6f + 0.4f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                }
        )
    }
}
