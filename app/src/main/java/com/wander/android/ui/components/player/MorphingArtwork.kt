package com.wander.android.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Decode size for the travelling cover. **Constant on purpose.**
 *
 * Deriving it from the measured bounds meant the first frames of a drag — before the full player
 * had reported its bounds — asked for a 48 dp bitmap, and the next frame asked for a 360 dp one.
 * That is a different `ImageRequest`, so Coil decoded a second bitmap and swapped it in: the
 * "small blurry cover that grows, then gets replaced by a big one" bug. One size, one request,
 * one bitmap, for the whole gesture.
 */
/** Clear of the rounded corner at full size, and off the artwork's busiest region. */
private val BadgeInset = 14.dp

private val MorphArtworkSize = 360.dp

/**
 * Corner as a percentage of the box, so the radius grows with the cover on its own. A fixed dp
 * radius would need a second animated value and would read as a small corner stretched across a
 * large image.
 */
private val MorphShape = RoundedCornerShape(percent = 12)

/** Space between the current cover and the neighbours peeking in either side of it. */
private val PeekGap = 16.dp

/**
 * The single cover art shared by the docked strip and the full player, plus the previous and next
 * covers waiting either side of it.
 *
 * Composed once; only its geometry changes, inside deferred `layout` lambdas, so dragging the
 * sheet or swiping the cover neither recomposes it nor re-requests the image.
 *
 * The neighbours are composed only while a swipe is in flight ([TrackSwipeState.isSwiping], which
 * flips twice per gesture rather than every frame) and are faded out entirely while the player is
 * docked — the strip is far too small for a three-cover filmstrip to read as anything but noise.
 */
@Composable
internal fun MorphingArtwork(
    url: String?,
    contentDescription: String?,
    anchors: PlayerArtworkAnchors,
    progress: () -> Float,
    rawProgress: () -> Float,
    visible: Boolean,
    swipe: TrackSwipeState,
    previousUrl: String?,
    nextUrl: String?,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    fingerprintStatus: com.wander.android.data.repository.FingerprintStatus =
        com.wander.android.data.repository.FingerprintStatus.MISSING,
    carouselEnabled: Boolean = true,
    /** The cover's own colour, for the backlight behind it. Null draws no glow. */
    glowColor: Color? = null
) {
    if (!visible) return

    // Checked here rather than inside the layout block below: the docked strip reports its bounds
    // one pass after the sheet first appears, and a `layout` lambda **must** measure its
    // measurable exactly once on every path. Bailing out inside it threw as soon as a track
    // started playing.
    val mini = anchors.miniBounds ?: return

    val showPeeks = swipe.isSwiping || (carouselEnabled && rawProgress() > 0.8f)
    if (showPeeks) {
        // Drawn before the current cover so it stays on top as the neighbours slide under it.
        if (canPrevious) {
            PeekArtwork(previousUrl, anchors, mini, progress, rawProgress, swipe, side = -1, carouselEnabled = carouselEnabled)
        }
        if (canNext) {
            PeekArtwork(nextUrl, anchors, mini, progress, rawProgress, swipe, side = 1, carouselEnabled = carouselEnabled)
        }
    }

    Box(
        // `graphicsLayer` goes *inside* `layout`, and the order is load-bearing. An alpha below 1
        // forces an offscreen layer, and that layer is clipped to the bounds of the node it sits
        // on. Outside the `layout` those bounds are this node's own box at the parent's origin,
        // while the cover is placed far down the screen at `rect.top` — so fading it showed only
        // the sliver where the two happened to overlap, which is the "top half of the cover"
        // effect. Inside, the layer is the placeable itself and travels with it.
        modifier = modifier
            .layout { measurable, _ ->
                val rect = anchors.currentRect(mini, rawProgress)
                val width = rect.width.roundToInt().coerceAtLeast(0)
                val height = rect.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                // The pitch the neighbours are spaced by, published so a committed swipe carries
                // this cover off by exactly one slot instead of an arbitrary distance.
                swipe.stepPx = rect.width + PeekGap.toPx()
                layout(width, height) {
                    placeable.place(
                        x = (rect.left + swipe.offsetX.value).roundToInt(),
                        y = rect.top.roundToInt()
                    )
                }
            }
            .graphicsLayer {
                this.alpha = alpha()
                if (carouselEnabled && progress() > 0.8f) {
                    val step = swipe.stepPx.takeIf { it > 0f } ?: FullExitDistance
                    val offset = swipe.offsetX.value
                    val progressAway = (abs(offset) / step).coerceIn(0f, 1f)
                    val scale = lerpFloat(1f, 0.85f, progressAway)
                    scaleX = scale
                    scaleY = scale
                    rotationY = (-offset / step * 12f).coerceIn(-14f, 14f)
                    cameraDistance = 12f * density
                }
            }
    ) {
        // The backlight, and it has to be drawn *here* — first child of the box that holds the
        // cover, so the cover is painted over it.
        //
        // It lived in `NowPlayingScreen` for a while, on a `drawBehind`, and that could never work:
        // `drawBehind` puts the drawing behind *that node*, but the whole screen is composed after
        // the travelling cover (see `PlayerSheetContent`'s draw order), so the glow landed on top
        // of the artwork whatever it did locally. Layer order is the fix, not draw order.
        //
        // Scaled past the cover's own bounds rather than drawn inside them: a glow confined to the
        // square would be entirely hidden behind an opaque album cover, which is what made moving
        // it in front look like the only option.
        glowColor?.let { seed ->
            val glow = seed.asBacklight()
            val reduceMotion = com.wander.android.ui.theme.LocalReducedMotion.current
            val brush = fluidAmbientBrush(
                color1 = glow,
                color2 = glow.driftedHue(),
                backdrop = Color.Transparent,
                reduceMotion = reduceMotion
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = GlowScale
                        scaleY = GlowScale
                        this.alpha = smoothStep(progress(), 0.65f, 1f)
                    }
                    .background(brush = brush, shape = CircleShape)
            )
        }

        Artwork(
            // While a skip is settling this is the cover the gesture already put in the slot; see
            // [TrackSwipeState.pendingArtworkUrl].
            url = swipe.pendingArtworkUrl ?: url,
            contentDescription = contentDescription,
            sizeDp = MorphArtworkSize,
            shape = MorphShape,
            // Nothing to cross-fade: this is one continuous element, and animating it is what made
            // the hand-off visible.
            crossfade = false,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom-left, and only once the sheet is open. On the docked strip the cover is a
        // thumbnail and a six-pixel dot on it would be lint rather than information, so it fades in
        // with the expansion rather than riding the cover all the way down.
        //
        // Drawn here rather than in `NowPlayingScreen` because this is the cover the user is
        // looking at: that screen's artwork slot is an empty box reporting bounds, so the badge it
        // contained was in a branch the real player never takes.
        com.wander.android.ui.components.FingerprintBadge(
            status = fingerprintStatus,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(BadgeInset)
                .graphicsLayer { this.alpha = smoothStep(progress(), 0.75f, 1f) }
        )
    }
}

/**
 * The cover one step either side of the current one, parked just off the edge of the current
 * cover's box and dragged in with the finger.
 *
 * [side] is -1 for the previous track (sitting to the left) and +1 for the next.
 */
/** How far past the cover the backlight spills. */
private const val GlowScale = 1.22f

/**
 * A cover's seed colour, made fit to glow with.
 *
 * **Never white**, and that is the whole reason this exists. The seed is whatever dominates the
 * artwork, so a sleeve that is mostly white paper — or any pale, washed-out cover — yields a near
 * white seed, and a white backlight is not a glow: it is a grey halo that looks like a rendering
 * fault, and under the AMOLED theme it is the brightest thing on a black screen.
 *
 * Saturation is floored so a near-grey seed still reads as a colour, and value is capped well below
 * full so the glow stays a light *behind* something rather than a lamp pointed at the reader. Hue is
 * never touched — that is the part actually taken from the artwork, and the part worth keeping.
 */
private fun Color.asBacklight(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = hsv[2].coerceIn(0.35f, 0.72f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The fluid shader's second colour: the same backlight, rotated round the colour wheel.
 *
 * A shader blending one colour with itself would just breathe in and out; the second hue is what
 * makes the drift read as *fluid* rather than as one colour pulsing.
 */
private fun Color.driftedHue(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + 40f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Where the cover sits right now, between the docked strip and the full player.
 *
 * Until the full player has been measured there is nowhere to travel to, so this stays on the
 * mini rect rather than interpolating towards a placeholder.
 */
internal fun PlayerArtworkAnchors.currentRect(mini: Rect, progress: () -> Float): Rect {
    val full = fullBounds ?: return mini
    val p = progress()
    // Past 1 the cover is overshooting its resting frame, and the easing curve cannot help: a
    // cubic bezier easing *throws* outside 0..1. Extrapolate linearly instead, which meets the
    // eased curve exactly at 1 (easing(1) == 1) and so stays continuous through the handover.
    val t = if (p > 1f) p else FastOutSlowInEasing.transform(p.coerceIn(0f, 1f))
    return lerp(mini, full, t)
}
