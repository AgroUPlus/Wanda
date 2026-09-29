package com.wander.android.ui.components.player

import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import com.wander.android.ui.components.MiniArtworkSize
import com.wander.android.ui.components.MiniRowVerticalPadding
import com.wander.android.ui.components.bouncySpec
import com.wander.android.ui.components.dockSpec
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

/**
 * Height of the docked strip, summed from what `MiniPlayer` actually lays out rather than guessed:
 * the artwork row's vertical padding either side of the cover. There is no separate progress-bar
 * row any more — playback progress is drawn as a ring around the play button itself, inside this
 * same row — so the strip is exactly the artwork row's own height.
 *
 * It was a flat 68 dp, which is 8 dp short of the real content. The strip is clipped to this value
 * below, so the bottom of the play controls was cut off, and every screen's list padding is derived
 * from it, so the last row of covers ended up under the strip.
 */
val MiniStripHeight: Dp = MiniRowVerticalPadding * 2 + MiniArtworkSize

/** Gap between the docked strip and the navigation bar, so the strip reads as floating over it. */
val MiniPlayerGap: Dp = 5.dp

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
    /**
     * The current track's own playback progress, 0f..1f — read only inside [drawBehind], same
     * discipline as everything else this file measures every frame of a drag. Drives the ambient
     * scrim's slow drift in `drawPlayerSheetBackground`; a caller with no cover-art theming enabled
     * can leave this at its default, since the flat fallback path never reads it.
     */
    seekProgress: () -> Float = { 0f },
    content: @Composable (progress: () -> Float, rawProgress: () -> Float, expandedHeight: Dp) -> Unit
) {
    // A real enter/exit rather than a hard `if`: the player used to simply appear or vanish
    // between frames whenever a track started or the queue ran out, while the dock row beside it
    // (`WanderDock`) already slid and scaled in on a spring. One chrome element cutting and the
    // other springing read as two different animations rather than the shell arriving as one
    // piece — this gives the sheet the same bouncy slide+fade `WanderDock` uses.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(bouncySpec()) { it / 2 },
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(bouncySpec()) { it / 2 },
        modifier = modifier.fillMaxSize()
    ) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val overdragLimitPx = with(density) { OverdragDistance.toPx() }

    // Decided once per gesture, at `onDragStarted` — see the `.draggable` block below for why a
    // plain per-frame check on `offset` would cut the give off mid-motion instead of only refusing
    // it to a finger that starts on an already-rested strip.
    var allowOverdragThisGesture by remember { mutableStateOf(true) }

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

    // The dock row appearing or leaving changes where the strip rests just as much as its own
    // height does, and this used to be handed straight to the travel maths below — the strip
    // teleported to its new resting place while the dock row itself was still springing. Same
    // discipline as above: animated here, read only in effects and `graphicsLayer`, on the same
    // spring the dock row animates on so the two stay in step.
    val bottomInsetState = animateDpAsState(
        targetValue = bottomInset,
        animationSpec = dockSpec(),
        label = "bottom-inset"
    )

    // Same discipline as `dockedHeightState` above: the target flips whenever navigation shows or
    // hides the dock row, and this eases between the two corner silhouettes rather than snapping,
    // read only inside `graphicsLayer` below — never in composition scope.
    val pairednessState = animateFloatAsState(
        targetValue = if (pairedWithDockRow) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "docked-pairedness"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight

        // How far the sheet has to travel, which the animated resting height moves. Collected
        // rather than computed in composition, for the reason above: `updateMaxOffset` snaps a
        // collapsed sheet onto the new anchor, so following the spring here is what carries the
        // docked strip down to its new resting place.
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
                    translationY = if (sheetState.maxOffsetPx > 0f) {
                        sheetState.offset.value
                    } else {
                        (sheetHeight - dockedHeightState.value - bottomInsetState.value - MiniPlayerGap).toPx()
                    }
                    applySheetChrome(sheetState, pairednessState.value)
                }
                // Drawn rather than composed: the colour changes every frame of a drag, and a
                // `background(...)` argument would recompose the sheet along with it.
                .drawBehind {
                    drawPlayerSheetBackground(baseColor, coverSeed, seekProgress())
                }
                .sheetLayout(sheetState, sheetHeight) { dockedHeightState.value }
                .draggable(
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
}
