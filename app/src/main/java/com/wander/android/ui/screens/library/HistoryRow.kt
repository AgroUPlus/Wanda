package com.wander.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.HistoryTrack
import com.wander.android.ui.components.GroupedInnerRadius
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.GroupedOuterRadius
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.selection.TrackSwipeBox
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PlayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * One play in [HistoryScreen]: the track, the time it was played, and the row's swipe shortcuts —
 * off while [swipeEnabled] is false, during a selection.
 */
@Composable
internal fun HistoryRow(
    entry: HistoryTrack,
    isFirstOfDay: Boolean,
    isLastOfDay: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onLongPress: (() -> Unit)?,
    selected: Boolean?,
    swipeEnabled: Boolean,
    zone: ZoneId
) {
    // The day-group card goes on the swipe box, so the pull's backdrop is clipped to it.
    TrackSwipeBox(
        track = entry.track,
        enabled = swipeEnabled,
        modifier = Modifier.dayGroupedItem(isFirstOfDay, isLastOfDay)
    ) {
        TrackRow(
            track = entry.track,
            onPlay = onPlay,
            onToggleLike = onToggleLike,
            onLongPress = onLongPress,
            selected = selected,
            trailingLabel = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalTime().format(PlayTime)
        )
    }
}

/**
 * The grouped-card look ([groupedListItem][com.wander.android.ui.components.groupedListItem]),
 * driven by day-group edges instead of list position — a day's plays are the "card" here, not the
 * whole page, and a page boundary mid-day must not draw a seam paging alone did not create.
 */
@Composable
private fun Modifier.dayGroupedItem(isFirstOfDay: Boolean, isLastOfDay: Boolean): Modifier {
    val top = if (isFirstOfDay) GroupedOuterRadius else GroupedInnerRadius
    val bottom = if (isLastOfDay) GroupedOuterRadius else GroupedInnerRadius
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    return this
        .padding(horizontal = 16.dp)
        .padding(top = if (isFirstOfDay) 0.dp else GroupedItemGap)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
}
