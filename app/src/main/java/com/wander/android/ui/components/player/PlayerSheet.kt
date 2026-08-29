package com.wander.android.ui.components.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniProgressBarHeight
import com.wander.android.ui.components.MiniRowVerticalPadding
import com.wander.android.ui.navigation.DockRowHeight
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

/**
 * Height of the docked strip, summed from what `MiniPlayer` actually lays out rather than guessed:
 * the progress bar, then the artwork row's vertical padding either side of the cover.
 *
 * It was a flat 68 dp, which is 8 dp short of the real content. The strip is clipped to this value
 * below, so the bottom of the play controls was cut off, and every screen's list padding is derived
 * from it, so the last row of covers ended up under the strip.
 */
val MiniStripHeight: Dp = MiniProgressBarHeight + MiniRowVerticalPadding * 2 + MiniArtworkSize

/**
 * The whole docked block: the player strip *and* the dock row of destinations or search beneath it.
 *
 * The navigation bar used to be a separate `bottomBar` under a floating player card. Fusing them
 * means the sheet's docked area is taller by exactly one dock row, and because every content inset
 * in `WanderApp` is derived from this constant, widening it here is what moves every list's bottom
 * padding with it.
 */
val MiniPlayerHeight: Dp = MiniStripHeight + DockRowHeight

/** Gap between the docked strip and the navigation bar, so the strip reads as floating over it. */
val MiniPlayerGap: Dp = 8.dp

/**
 * The strip's drop shadow is drawn *outside* its clip box, so content scrolled to the very bottom
 * sits under the shadow even when it clears the strip itself. Reserved on top of [MiniPlayerGap].
 */
val MiniPlayerShadowInset: Dp = 6.dp

/**
 * Inset on each side while docked, giving the strip its floating-card look. It animates away as
 * the sheet opens so the expanded player is full-bleed — without re-measuring the content, which
 * is measured once at the docked width and simply centred as the box widens.
 */
private val DockedSideInset: Dp = 12.dp

/** Corner radius while docked; interpolated to square as the sheet fills the screen. */
private val DockedCorner: Dp = 28.dp

/**
 * The player as one continuously draggable surface.
 *
 * **Nothing here reads `sheetState.progress` during composition.** It changes every frame of a
 * drag, and reading it in composition scope recomposed this composable — and with it the whole
 * player, `NowPlayingScreen` included — on every frame, re-measuring the entire tree as the
 * surface's height and padding changed. Every use is now inside a deferred `layout` or
 * `graphicsLayer` lambda, and the content slot receives a `() -> Float` so it can do the same.
 *
 * Two of them, in fact: `progress` is clamped to 0..1 and is what almost everything wants, while
 * `rawProgress` keeps the spring's overshoot for the one element that animates past its resting
 * frame. The sheet's own radius and box lerp deliberately stay on the clamped one.
 *
 * The content is measured **once**, at a constant size; only the node's drawn box animates.
 */
@Composable
fun PlayerSheet(
    sheetState: PlayerSheetState,
    bottomInset: Dp,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    /**
     * How tall the sheet is while docked: [MiniPlayerHeight] with a dock row beneath the strip,
     * [MiniStripHeight] on the screens that show the player alone. It decides both where the
     * sheet rests and how far it has to travel, so it cannot be assumed — a sheet that rests one
     * dock row lower than it measures leaves a strip-sized hole above the navigation bar.
     */
    dockedHeight: Dp = MiniPlayerHeight,
    content: @Composable (progress: () -> Float, rawProgress: () -> Float, expandedHeight: Dp) -> Unit
) {
    if (!isVisible) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Navigating between a root and a page beneath it takes a dock row out from under the strip,
    // so the sheet's resting height changes by that much. Springing it — rather than cutting —
    // is what makes the player *settle* onto the screen it landed on instead of teleporting, and
    // the strip's own contents ride down with it because they are laid out from its top edge.
    //
    // Deliberately never read in composition scope: this file measures the player exactly once
    // and animates the drawn box, and a `by` here would recompose the whole player on every frame
    // of the spring. Every read below is inside a `layout`, a `graphicsLayer` or an effect.
    val dockedHeightState = animateDpAsState(
        targetValue = dockedHeight,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "docked-height"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sheetHeight = maxHeight

        // How far the sheet has to travel, which the animated resting height moves. Collected
        // rather than computed in composition, for the reason above: `updateMaxOffset` snaps a
        // collapsed sheet onto the new anchor, so following the spring here is what carries the
        // docked strip down to its new resting place.
        LaunchedEffect(sheetHeight, bottomInset, density) {
            snapshotFlow { dockedHeightState.value }
                .distinctUntilChanged()
                .collect { docked ->
                    val travel = with(density) {
                        (sheetHeight - docked - bottomInset - MiniPlayerGap).toPx()
                    }
                    sheetState.updateMaxOffset(travel)
                }
        }

        BackHandler(enabled = sheetState.isExpanded) {
            scope.launch { sheetState.collapse() }
        }

        // Two colours, not one. Docked, this is a card lifted off the screen and wants a raised
        // container; expanded, it *is* the screen and wants the plain background — which is what
        // makes it honour the OLED theme. Pinned to `surfaceContainerHigh` it stayed #1A1A1A with
        // pure black switched on, so the one screen that fills the panel was the one screen that
        // never went black.
        val dockedColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val expandedColor = MaterialTheme.colorScheme.background

        // Modifier order matters here. Outside in:
        //   graphicsLayer  — translation, corner and shadow, clipping to the *animated* box
        //   background     — painted at that same animated box, so it reaches the screen edges
        //                    when expanded
        //   layout         — reports the animated box upward while measuring the content ONCE,
        //                    at a constant size, so nothing inside re-measures during a drag
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val progress = sheetState.progress
                    translationY = if (sheetState.maxOffsetPx > 0f) {
                        sheetState.offset.value
                    } else {
                        (sheetHeight - dockedHeightState.value - bottomInset - MiniPlayerGap).toPx()
                    }
                    val radius = DockedCorner.toPx() * (1f - progress)
                    shape = RoundedCornerShape(
                        topStart = radius,
                        topEnd = radius,
                        // Square against the screen edge only at the very end of the travel.
                        bottomStart = radius * (1f - progress),
                        bottomEnd = radius * (1f - progress)
                    )
                    clip = true
                    shadowElevation = (6.dp + 2.dp * progress).toPx()
                }
                // Drawn rather than composed: the colour changes every frame of a drag, and a
                // `background(...)` argument would recompose the sheet along with it.
                .drawBehind {
                    drawRect(lerpColor(dockedColor, expandedColor, sheetState.progress))
                }
                .layout { measurable, constraints ->
                    val fullWidth = constraints.maxWidth
                    val dockedWidth = fullWidth - DockedSideInset.roundToPx() * 2
                    val fullHeight = sheetHeight.roundToPx()
                    val miniHeight = dockedHeightState.value.roundToPx()

                    // One measurement for the whole gesture.
                    val placeable = measurable.measure(
                        Constraints.fixed(dockedWidth, fullHeight)
                    )

                    val progress = sheetState.progress
                    val width = lerp(dockedWidth, fullWidth, progress)
                    val height = lerp(miniHeight, fullHeight, progress)

                    // Reporting the animated height is what keeps the docked strip from covering
                    // the navigation bar: a full-height node would paint over everything below
                    // its top edge.
                    layout(width, height) {
                        placeable.place(x = (width - dockedWidth) / 2, y = 0)
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { sheetState.dragBy(delta) }
                    },
                    onDragStopped = { velocity ->
                        scope.launch { sheetState.settle(velocity) }
                    }
                )
        ) {
            // A bare Box, unlike Surface, sets no content colour — so every Text and Icon in the
            // player fell back to the default and rendered black on a dark surface.
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(dockedColor)
            ) {
                content({ sheetState.progress }, { sheetState.rawProgress }, sheetHeight)
            }
        }
    }
}
