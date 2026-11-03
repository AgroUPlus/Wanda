package com.wander.android.ui.components.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * How far a drag can pull the docked strip past its resting position before the rubber band
 * effectively stops it — see [PlayerSheetState.dragBy]. Never fully reached: [rubberBand] only
 * approaches it asymptotically, so this is a ceiling on the give, not a distance the strip travels.
 */
private val OverdragDistance: Dp = 64.dp

/**
 * Keeps the sheet's travel distance in step with the animated resting height.
 *
 * Collected rather than computed in composition, for the reason given at `dockedHeightState`:
 * `updateMaxOffset` snaps a collapsed sheet onto the new anchor, so following the spring here is what
 * carries the docked strip down to its new resting place.
 */
@Composable
internal fun SheetTravelEffect(
    sheetState: PlayerSheetState,
    sheetHeight: Dp,
    dockedHeightState: State<Dp>,
    bottomInsetState: State<Dp>
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(sheetHeight, density) {
        snapshotFlow { dockedHeightState.value to bottomInsetState.value }
            .distinctUntilChanged()
            .collect { (docked, inset) ->
                val travel = with(density) {
                    (sheetHeight - docked - inset - MiniPlayerGap).toPx()
                }
                sheetState.updateMaxOffset(travel, scope)
            }
    }
}

@Composable
internal fun SheetBackHandler(sheetState: PlayerSheetState) {
    val haptics = rememberHaptics()
    PredictiveBackHandler(enabled = sheetState.isBackHandlerEnabled) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                sheetState.updatePredictiveBackProgress(backEvent.progress, backEvent.swipeEdge)
            }
            // The swipe let go past the point of no return: the same "released into place" a
            // dragged collapse gets, answered at the commit rather than when the spring lands.
            haptics.settled()
            sheetState.collapse()
        } catch (e: CancellationException) {
            sheetState.expand()
        }
    }
}

/**
 * One finger's vertical drag on the sheet, decided when it lands and read only from the gesture
 * callbacks — never in composition, so plain fields rather than snapshot state.
 */
private class SheetDragSession {
    /** The finger landed on a strip already fully at rest. */
    var fromRest = false

    /** That rest-started drag can end in a dismiss; see [DockedDismiss.isAllowed]. */
    var canDismiss = false

    /** Finger travel since landing, for a rest-started drag; positive is down. */
    var pullPx = 0f

    /** The sheet moved within its own travel at some point, not only into the rubber band. */
    var leftRest = false
}

/**
 * The vertical drag that moves the sheet between docked and expanded, and — given [dismiss] —
 * pulls the resting strip down to put the player away.
 *
 * [sheetHeight] is how far a committed dismiss carries the sheet: past it, the strip is out of view.
 */
@Composable
internal fun Modifier.sheetDraggable(
    sheetState: PlayerSheetState,
    dismiss: DockedDismiss?,
    sheetHeight: Dp
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val overdragLimitPx = with(density) { OverdragDistance.toPx() }
    val dismissDistancePx = with(density) { DockedDismissDistance.toPx() }
    val flingDistancePx = with(density) { DockedDismissFlingDistance.toPx() }
    val sheetHeightPx = with(density) { sheetHeight.toPx() }
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val session = remember { SheetDragSession() }

    return draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            if (session.fromRest) {
                session.pullPx = (session.pullPx + delta).coerceAtLeast(-sheetState.maxOffsetPx)
                if (session.pullPx < 0f) session.leftRest = true
            }
            scope.launch {
                when {
                    session.canDismiss -> sheetState.pullDocked(session.pullPx, overdragLimitPx)
                    session.fromRest -> sheetState.dragBy(delta, 0f)
                    else -> sheetState.dragBy(delta, overdragLimitPx)
                }
            }
        },
        // Overdrag on a strip that is *already* at rest used to be refused outright: with nowhere
        // for the pull to go, the give read as the mini player answering a touch that did
        // nothing. A pull from rest now does something — far enough, or flung, it dismisses the
        // player — so the give is back for exactly that gesture, as the visible "keep going".
        // Where a dismiss is not allowed the old rule stands. A drag that started mid-transition
        // keeps its little extra give at the end of the trip, as before. All of it is decided
        // once, as the finger lands: re-checked every frame, the give would cut out mid-motion
        // the instant a genuine collapse reached rest.
        onDragStarted = {
            session.fromRest = sheetState.offset.value >= sheetState.maxOffsetPx
            session.canDismiss = session.fromRest && dismiss != null && dismiss.isAllowed()
            session.pullPx = 0f
            session.leftRest = !session.fromRest
        },
        onDragStopped = { velocity ->
            val dismissing = session.canDismiss &&
                commitsDockedDismiss(session.pullPx, velocity, dismissDistancePx, flingDistancePx)
            if (dismissing && dismiss != null) {
                haptics.confirmed()
                scope.launch { sheetState.slideOutDocked(sheetHeightPx, velocity, slideSpec) }
                dismiss.onDismiss()
            } else {
                // Only a sheet the finger actually carried between anchors buzzes on landing: a
                // strip springing back out of the rubber band has not changed state, and a settle
                // interrupted by another animation never reaches the line below.
                scope.launch {
                    if (sheetState.settle(velocity) && session.leftRest) haptics.settled()
                }
            }
        }
    )
}
