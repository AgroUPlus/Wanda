package com.wander.android.ui.components.player

import androidx.activity.BackEventCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

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
 * Corner silhouette, shadow and predictive-back pose of the player card, applied inside the
 * sheet's `graphicsLayer` so every read of `sheetState` stays out of composition scope.
 */
internal fun GraphicsLayerScope.applySheetChrome(sheetState: PlayerSheetState, pairedness: Float) {
    val progress = sheetState.progress
    val backVisual = sheetState.predictiveBackVisual
    val backRadius = PredictiveBackCorner.toPx() * backVisual
    // Exterior (top) vs interior (bottom, facing the dock row) at pairedness 1,
    // both equal to a full stadium at pairedness 0 (alone) — see `AloneCorner`'s
    // doc.
    val p = pairedness
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

/**
 * Reports the animated box upward while measuring the content once, at a constant size.
[dockedHeight] is a lambda so the spring is read only at layout time, never in composition.
 */
internal fun Modifier.sheetLayout(
    sheetState: PlayerSheetState,
    sheetHeight: Dp,
    dockedHeight: () -> Dp
): Modifier = layout { measurable, constraints ->
    val fullWidth = constraints.maxWidth
    val dockedWidth = fullWidth - DockedSideInset.roundToPx() * 2
    val fullHeight = sheetHeight.roundToPx()
    val miniHeight = dockedHeight().roundToPx()

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
    // is centred and the sheet's `graphicsLayer` clip trims `DockedSideInset` off
    // each side. The strip puts that inset back as padding of its own — see
    // `MiniPlayer` in `PlayerSheetContent` — landing its content in exactly the box
    // it occupied when the measurement itself was docked-width.
    layout(width, height) {
        placeable.place(x = (width - fullWidth) / 2, y = 0)
    }
}
