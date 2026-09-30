package com.wander.android.ui.components.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    PredictiveBackHandler(enabled = sheetState.isBackHandlerEnabled) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                sheetState.updatePredictiveBackProgress(backEvent.progress, backEvent.swipeEdge)
            }
            sheetState.collapse()
        } catch (e: CancellationException) {
            sheetState.expand()
        }
    }
}

/** The vertical drag that moves the sheet between docked and expanded. */
@Composable
internal fun Modifier.sheetDraggable(sheetState: PlayerSheetState): Modifier {
    val scope = rememberCoroutineScope()
    val overdragLimitPx = with(LocalDensity.current) { OverdragDistance.toPx() }

    // Decided once per gesture, at `onDragStarted` — see `onDragStarted` below for why a
    // plain per-frame check on `offset` would cut the give off mid-motion instead of only refusing
    // it to a finger that starts on an already-rested strip.
    var allowOverdragThisGesture by remember { mutableStateOf(true) }

    return draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            scope.launch { sheetState.dragBy(delta, if (allowOverdragThisGesture) overdragLimitPx else 0f) }
        },
        // Overdrag only means something mid-transition — a collapse that arrives at
        // rest still carrying the finger's motion, and gets a little extra give at the
        // end of its trip. A finger placed on the strip *after* it is already fully at
        // rest and pulled down has nowhere real to go: it used to still rubber-band
        // every time regardless of how the gesture started, which read as the docked
        // mini player responding to a touch that does nothing. Decided once, at the
        // moment the finger lands — not re-checked every frame of the same drag, or the
        // give would cut out mid-motion the instant a genuine collapse reaches rest.
        onDragStarted = {
            allowOverdragThisGesture = sheetState.offset.value < sheetState.maxOffsetPx
        },
        onDragStopped = { velocity ->
            scope.launch { sheetState.settle(velocity) }
        }
    )
}
