package com.wander.android.ui.screens.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.rememberHaptics
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The unified queue timeline: a single continuous list of all tracks.
 * Past tracks sit above the currently playing track with muted opacity,
 * the active song is seamlessly highlighted with its animated equalizer,
 * and upcoming tracks sit below. Every track can be reordered or swiped to remove.
 */
@Composable
internal fun QueueUpNext(
    entries: List<QueueEntry>,
    canReorder: Boolean,
    onPlay: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    onRemove: (QueueEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    val currentIdx = entries.indexOfFirst { it.role == QueueItemRole.CURRENT }
    LaunchedEffect(Unit) {
        if (currentIdx > 0) {
            listState.scrollToItem((currentIdx - 1).coerceAtLeast(0))
        }
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromEntry = entries.firstOrNull { it.key == from.key } ?: return@rememberReorderableLazyListState
        val toEntry = entries.firstOrNull { it.key == to.key } ?: return@rememberReorderableLazyListState
        onMove(fromEntry.queueIndex, toEntry.queueIndex)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(entries, key = { it.key }, contentType = { "queue-track" }) { entry ->
                ReorderableItem(reorderState, key = entry.key, enabled = canReorder) { isDragging ->
                    val dismissState = rememberSwipeToDismissBoxState()

                    LaunchedEffect(entry.key) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        gesturesEnabled = !isDragging,
                        onDismiss = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                haptics.settled()
                                onRemove(entry)
                            }
                        },
                        backgroundContent = { RemoveBackdrop() }
                    ) {
                        Surface(
                            tonalElevation = if (isDragging) DraggingElevation else 0.dp,
                            shadowElevation = if (isDragging) DraggingElevation else 0.dp,
                            // Opaque even at rest, matching the sheet's own container colour —
                            // `SwipeToDismissBox` keeps `RemoveBackdrop` composed and stacked
                            // directly behind this at full size the whole time, only sliding this
                            // foreground aside to reveal it. A transparent resting colour let it
                            // bleed through every gap the row itself didn't paint over (the drag
                            // handle's margin, the space right of the title) — the "Remove" icon
                            // and label peeking out behind every track, not just a swiped one.
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            val isCurrent = entry.role == QueueItemRole.CURRENT
                            val isPrevious = entry.role == QueueItemRole.PREVIOUS

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (isPrevious) 0.60f else 1f)
                            ) {
                                if (canReorder) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .draggableHandle(
                                                onDragStarted = { haptics.heldDown() },
                                                onDragStopped = { haptics.settled() }
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Reorder ${entry.track.title}",
                                            tint = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                TrackRow(
                                    track = entry.track,
                                    isPlaying = isCurrent,
                                    // The highlight is drawn once, behind the whole list, and
                                    // travels to whichever row is current — see
                                    // [CurrentTrackHighlight]. A background painted by the row
                                    // itself as well would double up and fight the one that moves.
                                    showBackground = false,
                                    onPlay = { onPlay(entry.queueIndex) },
                                    onLongPress = { onLongPress(entry.track) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Drawn *after* the list, not before it: each row's own `Surface` is opaque at rest (see
        // the comment on it — that's load-bearing for hiding `RemoveBackdrop` between rows), so a
        // highlight drawn behind the list the way this used to be was entirely covered by it and
        // never visible. A translucent overlay on top reads as the same "current row" tint without
        // needing the per-row surface to know anything about it, and draws no pointer input of its
        // own, so scrolling and swiping the row underneath is untouched.
        CurrentTrackHighlight(listState = listState, currentKey = entries.getOrNull(currentIdx)?.key)
    }
}

/**
 * One highlight, drawn behind the whole list, that eases from the row that was playing to the row
 * that is now playing instead of each row snapping its own background on and off.
 *
 * Tracks the current row's live bounds from [LazyListState.layoutInfo] — which already accounts
 * for scrolling — so the highlight keeps pace with the list for free while scrolling, and only
 * springs into its new position when the selection itself changes. It fades out rather than
 * jumping across the gap when the newly-current row has scrolled out of view.
 */
@Composable
private fun CurrentTrackHighlight(
    listState: LazyListState,
    currentKey: String?
) {
    val density = LocalDensity.current
    val targetBounds by remember(currentKey) {
        derivedStateOf {
            if (currentKey == null) {
                null
            } else {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == currentKey }
                    ?.let { it.offset.toFloat() to it.size.toFloat() }
            }
        }
    }

    val offsetAnim = remember { Animatable(targetBounds?.first ?: 0f) }
    val heightAnim = remember { Animatable(targetBounds?.second ?: 0f) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (targetBounds != null) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "queueHighlightAlpha"
    )

    // Read here, in composition, and held for the effect below — `Animatable.animateTo` is not
    // composable and cannot reach into the theme itself.
    val travelSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // The first real bounds this composable ever sees — the queue just opened, or the highlight
    // is appearing for the first time — snap straight there instead of springing in from zero.
    // The current track is already what's highlighted; it doesn't need to visibly fly onto it.
    // A track change later, with the queue still open, is a real selection change and keeps the
    // spring below.
    var hasAppeared by remember { mutableStateOf(false) }

    LaunchedEffect(targetBounds, listState.isScrollInProgress) {
        val bounds = targetBounds ?: return@LaunchedEffect
        if (!hasAppeared) {
            offsetAnim.snapTo(bounds.first)
            heightAnim.snapTo(bounds.second)
            hasAppeared = true
        } else if (listState.isScrollInProgress) {
            // Follow the scroll 1:1 — springing at the same time would make the highlight lag
            // behind the row it belongs to.
            offsetAnim.snapTo(bounds.first)
            heightAnim.snapTo(bounds.second)
        } else {
            offsetAnim.animateTo(bounds.first, travelSpec)
            heightAnim.animateTo(bounds.second, travelSpec)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetAnim.value.roundToInt()) }
            .height(with(density) { heightAnim.value.toDp() })
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .alpha(alphaAnim)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                MaterialTheme.shapes.medium
            )
    )
}

/** What the swipe reveals underneath: the consequence, named, in the colour of consequences. */
@Composable
private fun RemoveBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Remove",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private val DraggingElevation = 6.dp
