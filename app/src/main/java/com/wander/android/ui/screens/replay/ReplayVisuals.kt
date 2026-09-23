package com.wander.android.ui.screens.replay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * The playful layer under and inside the cards: the big shape tumbling behind each one, and
 * covers and faces cut into Material shapes.
 */

/**
 * One oversized Material shape, half off the bottom-right edge, turning slowly. Flat and faint —
 * it is there to make every card feel like a poster, never to compete with the number on it.
 *
 * Still under reduced motion: the shape stays, the spin does not.
 *
 * Clipped to its own page: the shape hangs past the right edge, and unclipped that overhang rode
 * along onto the next card as it slid in, lingering on the left until the pager let go of the
 * old page.
 */
@Composable
internal fun ReplayBackdrop(shape: RoundedPolygon, modifier: Modifier = Modifier) {
    val rotation = rememberSpin(BackdropTurnMs)
    val tint = LocalContentColor.current.copy(alpha = BackdropAlpha)

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val edge = maxWidth * BackdropScale
        Box(
            modifier = Modifier
                .size(edge)
                .offset(x = maxWidth - edge * BackdropInset, y = maxHeight - edge * BackdropInset)
                .graphicsLayer { rotationZ = rotation() }
                .clip(shape.toShape())
                .background(tint)
        )
    }
}

/**
 * A cover or a face in an expressive shape.
 *
 * With no picture to show, the shape is filled in the card's own colour with the name's initial
 * on it — a bold letter is a better stand-in on a poster than a generic music-note placeholder.
 */
@Composable
internal fun ReplayShapedArt(
    url: String?,
    name: String,
    shape: RoundedPolygon,
    size: Dp,
    modifier: Modifier = Modifier,
    spinning: Boolean = false
) {
    val rotation = if (spinning) rememberSpin(ArtTurnMs) else ({ 0f })
    val clip = shape.toShape()
    val art = modifier
        .size(size)
        .graphicsLayer { rotationZ = rotation() }

    if (url.isNullOrBlank()) {
        Box(
            modifier = art
                .clip(clip)
                .background(LocalContentColor.current.copy(alpha = InitialAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.displaySmallEmphasized,
                fontWeight = FontWeight.Black,
                // Counter-rotated so the letter stays upright while its shape turns.
                modifier = Modifier.graphicsLayer { rotationZ = -rotation() }
            )
        }
    } else {
        Artwork(
            url = url,
            contentDescription = name,
            sizeDp = size,
            shape = clip,
            crossfade = true,
            modifier = art
        )
    }
}

/**
 * A number stamped on an expressive shape, inverted: the shape in the card's content colour, the
 * text cut out in its background. The shape turns; the number stays upright.
 */
@Composable
internal fun ReplayBadge(text: String, shape: RoundedPolygon, modifier: Modifier = Modifier) {
    val rotation = rememberSpin(ArtTurnMs)
    val clip = shape.toShape()
    Box(modifier = modifier.size(BadgeSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation() }
                .clip(clip)
                .background(LocalContentColor.current)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.displayMediumEmphasized,
            fontWeight = FontWeight.Black,
            color = LocalReplayContainer.current,
            maxLines = 1
        )
    }
}

/**
 * A slow, endless turn in degrees, read as a lambda so only the draw phase follows it. Zero under
 * reduced motion.
 */
@Composable
private fun rememberSpin(turnMs: Int): () -> Float {
    if (LocalReducedMotion.current) return { 0f }
    val transition = rememberInfiniteTransition(label = "replaySpin")
    val degrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = FullTurn,
        animationSpec = infiniteRepeatable(tween(turnMs, easing = LinearEasing), RepeatMode.Restart),
        label = "replaySpinDegrees"
    )
    return { degrees }
}

private const val FullTurn = 360f
private const val BackdropTurnMs = 40_000
private const val ArtTurnMs = 24_000
private const val BackdropAlpha = 0.12f
private const val InitialAlpha = 0.22f

/** How big the backdrop is against the card's width, and how much of it stays on screen. */
private const val BackdropScale = 1.1f
private const val BackdropInset = 0.62f

private val BadgeSize = 176.dp
internal val ReplayHeroArt = 200.dp
internal val ReplayAvatar = 52.dp
