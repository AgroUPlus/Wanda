package com.wander.android.ui.components.player

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.util.lerp
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniProgressBarHeight
import com.wander.android.ui.components.MiniRowVerticalPadding
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
/**
 * How far the docked strip is held off each side of the screen.
 *
 * The sheet's content is measured once at the full screen width, so this is no longer subtracted
 * from that measurement — the strip applies it as padding of its own instead, and the sheet's clip
 * trims the same amount while docked. See the `layout` block below.
 */
internal val DockedSideInset: Dp = 12.dp

/**
 * Corner radii while docked, interpolated to square as the sheet fills the screen (unchanged) —
 * and, new, interpolated between two different *silhouettes* depending on whether a dock row is
 * floating just beneath the strip (see `WanderAppDock.kt` — it's its own independent card now,
 * shaped the same way from the other side — see `WanderDock`).
 *
 * Alone, every corner heads toward [AloneCorner] — half the strip's own height, a true stadium,
 * "circled" the way a lone pill button already reads elsewhere in the app. Paired, the top edge
 * (facing away from the dock row — the *exterior*) stays close to that same stadium curve at
 * [PairedExteriorCorner], while the bottom edge (facing the dock row — the *interior*, where the
 * gap between the two cards is) flattens toward [PairedInteriorCorner], a rounded square rather
 * than a curve. Read together with the dock row's own mirrored shape, the two cards read as one
 * continuous pill that got pulled apart in the middle, not two unrelated rectangles that happen
 * to be near each other.
 */
private val AloneCorner: Dp = MiniStripHeight / 2
private val PairedExteriorCorner: Dp = 32.dp
private val PairedInteriorCorner: Dp = 14.dp

/** How much the sheet shrinks at the very end of a predictive-back gesture. */
private const val PredictiveBackMinScale = 0.9f

/** How far the sheet shifts toward the swipe's opposite edge at the end of the gesture. */
private val PredictiveBackMaxShift: Dp = 8.dp

/** The corner radius the card takes on at the far end of a back swipe. */
private val PredictiveBackCorner: Dp = 28.dp

/**
 * How far a drag can pull the docked strip past its resting position before the rubber band
 * effectively stops it — see [PlayerSheetState.dragBy]. Never fully reached: [rubberBand] only
 * approaches it asymptotically, so this is a ceiling on the give, not a distance the strip travels.
 */
private val OverdragDistance: Dp = 64.dp

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
 * `rawProgress` keeps the spring's overshoot for the cover that animates past its resting frame,
 * and for the peek neighbours spaced off that same box — they have to agree, or the filmstrip is
 * pitched from one rect while the cover between them is drawn at another. The sheet's own radius
 * and box lerp deliberately stay on the clamped one.
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
     * How tall the sheet is while docked — [MiniStripHeight], always, now that the dock row is
     * its own independent card rather than something this height used to reserve room for. It
     * still decides both where the sheet rests and how far it has to travel, so it cannot be
     * assumed: paired with [pairedWithDockRow] and [bottomInset], see `WanderAppDock.kt`'s
     * `calculateDockMetrics` for how the dock row's own height is cleared instead — by
     * [bottomInset], not by this.
     */
    dockedHeight: Dp = MiniStripHeight,
    /**
     * The current track's cover-derived seed colour, null when cover-art theming is off. Darkened
     * and blended into the surface — see `drawPlayerSheetBackground` — the same amount whether
     * docked or expanded, so the strip and the full player it grows into are one continuous
     * surface rather than two different shades of it.
     */
    coverSeed: Color? = null,
    /** Whether the (now independent) dock row is currently floating just beneath the strip. */
    pairedWithDockRow: Boolean = false,
    content: @Composable (progress: () -> Float, rawProgress: () -> Float, expandedHeight: Dp) -> Unit
) {
    if (!isVisible) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val overdragLimitPx = with(density) { OverdragDistance.toPx() }

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

    // Same discipline as `dockedHeightState` above: the target flips whenever navigation shows or
    // hides the dock row, and this eases between the two corner silhouettes rather than snapping,
    // read only inside `graphicsLayer` below — never in composition scope.
    val pairednessState = animateFloatAsState(
        targetValue = if (pairedWithDockRow) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "docked-pairedness"
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
                    sheetState.updateMaxOffset(travel, scope)
                }
        }

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

        // One colour, not two — docked and expanded now paint the same surface, and
        // `drawPlayerSheetBackground` no longer scales the cover tint by how open the sheet is
        // either, so this is genuinely one flat colour at every point of the drag, not just the
        // same two endpoints with a different shade in between.
        //
        // This used to be `surfaceContainerHigh` while docked and `background` while expanded, on
        // the idea that docked is a card lifted off the screen and expanded *is* the screen. Real
        // enough, but the whole app is already wrapped in a cover-tinted theme (`CoverTintedTheme`)
        // whenever a track with cover-art theming is playing, and that tint lands differently on
        // `surfaceContainerHigh` than it does on `background` — two roles the same seed colour
        // pushes to two different final colours. The strip's background didn't match the full
        // player it grows into; it jumped to a different shade the moment the drag started.
        val baseColor = MaterialTheme.colorScheme.background

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
                    val backVisual = sheetState.predictiveBackVisual
                    val backRadius = PredictiveBackCorner.toPx() * backVisual
                    // Exterior (top) vs interior (bottom, facing the dock row) at pairedness 1,
                    // both equal to a full stadium at pairedness 0 (alone) — see `AloneCorner`'s
                    // doc.
                    val p = pairednessState.value
                    val exteriorBase = lerpDp(AloneCorner, PairedExteriorCorner, p).toPx()
                    val interiorBase = lerpDp(AloneCorner, PairedInteriorCorner, p).toPx()
                    val exteriorRadius = maxOf(exteriorBase * (1f - progress), backRadius)
                    val interiorRadius = maxOf(interiorBase * (1f - progress), backRadius)
                    shape = RoundedCornerShape(
                        topStart = exteriorRadius,
                        topEnd = exteriorRadius,
                        // Square against the screen edge only at the very end of the travel —
                        // unless a back swipe has lifted the whole card off the edges.
                        bottomStart = maxOf(interiorRadius * (1f - progress), backRadius),
                        bottomEnd = maxOf(interiorRadius * (1f - progress), backRadius)
                    )
                    clip = true
                    shadowElevation = (6.dp + 2.dp * progress).toPx()

                    // A predictive-back swipe shrinks the whole card toward its centre, rounds its
                    // corners and nudges it away from the edge being swiped from — the system's
                    // own full-screen back pose. Purely visual: `offset`/`progress` above still own
                    // the collapse, which starts only once the gesture commits.
                    if (backVisual > 0f) {
                        val scale = lerp(1f, PredictiveBackMinScale, backVisual)
                        scaleX = scale
                        scaleY = scale
                        val direction = if (sheetState.predictiveBackSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX += direction * PredictiveBackMaxShift.toPx() * backVisual
                    }
                }
                // Drawn rather than composed: the colour changes every frame of a drag, and a
                // `background(...)` argument would recompose the sheet along with it.
                .drawBehind {
                    drawPlayerSheetBackground(baseColor, coverSeed)
                }
                .layout { measurable, constraints ->
                    val fullWidth = constraints.maxWidth
                    val dockedWidth = fullWidth - DockedSideInset.roundToPx() * 2
                    val fullHeight = sheetHeight.roundToPx()
                    val miniHeight = dockedHeightState.value.roundToPx()

                    // One measurement for the whole gesture — at the *full* width, not the docked
                    // one. Measured at `dockedWidth`, an expanded sheet reported a `fullWidth` node
                    // with content 24 dp narrower centred in it, and the background showed through
                    // as a 12 dp bar down each edge. Nothing notices in the standard layout, where
                    // the cover is a centred square with padding of its own, but an edge-to-edge
                    // cover ends up framed in `background` on two sides and bled to the screen on
                    // the other two, which is what made it look like it had escaped its frame.
                    //
                    // Still exactly one measurement, so the drag stays as cheap as it was.
                    val placeable = measurable.measure(
                        Constraints.fixed(fullWidth, fullHeight)
                    )

                    val progress = sheetState.progress
                    val width = lerp(dockedWidth, fullWidth, progress)
                    val height = lerp(miniHeight, fullHeight, progress)

                    // Reporting the animated height is what keeps the docked strip from covering
                    // the navigation bar: a full-height node would paint over everything below
                    // its top edge.
                    //
                    // The x is negative while docked: the content is now wider than the node, so it
                    // is centred and the `graphicsLayer` clip above trims `DockedSideInset` off
                    // each side. The strip puts that inset back as padding of its own — see
                    // `MiniPlayer` in `PlayerSheetContent` — landing its content in exactly the box
                    // it occupied when the measurement itself was docked-width.
                    layout(width, height) {
                        placeable.place(x = (width - fullWidth) / 2, y = 0)
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { sheetState.dragBy(delta, overdragLimitPx) }
                    },
                    onDragStopped = { velocity ->
                        scope.launch { sheetState.settle(velocity) }
                    }
                )
        ) {
            // A bare Box, unlike Surface, sets no content colour — so every Text and Icon in the
            // player fell back to the default and rendered black on a dark surface.
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(baseColor)
            ) {
                content({ sheetState.progress }, { sheetState.rawProgress }, sheetHeight)
            }
        }
    }
}
