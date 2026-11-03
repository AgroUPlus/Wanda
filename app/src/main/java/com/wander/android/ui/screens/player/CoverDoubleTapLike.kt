package com.wander.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.theme.OnCoverArt
import kotlin.math.roundToInt

/**
 * Double-tap the cover to like — and only to like. The Instagram rule: a double tap on something
 * already liked plays the heart again and changes nothing, because unliking on a gesture this easy
 * to repeat would quietly undo the very thing the user meant to say twice.
 *
 * Holds the heart currently on screen. Each double tap replaces it with a fresh one at the new
 * spot, so tapping again mid-animation restarts the pop instead of stacking hearts.
 */
@Stable
internal class CoverDoubleTapLike(private val likeIfNotLiked: () -> Unit) {
    internal var pop by mutableStateOf<HeartPop?>(null)

    /** Where the second tap landed, in the coordinates of the box the heart is drawn over. */
    fun onDoubleTap(at: Offset) {
        pop = HeartPop(at, (pop?.id ?: 0) + 1)
        likeIfNotLiked()
    }
}

internal data class HeartPop(val at: Offset, val id: Int)

@Composable
internal fun rememberCoverDoubleTapLike(
    track: UnifiedTrack,
    isLiked: Boolean,
    onLike: (UnifiedTrack) -> Unit
): CoverDoubleTapLike {
    // Read through these: the tap detector is keyed on `Unit` and outlives every track change.
    val haptics by rememberUpdatedState(rememberHaptics())
    val currentTrack by rememberUpdatedState(track)
    val currentLiked by rememberUpdatedState(isLiked)
    val currentOnLike by rememberUpdatedState(onLike)
    return remember {
        CoverDoubleTapLike {
            // Only a like that actually happens is felt; replaying the heart over a liked track
            // changed nothing, so there is no new state for a vibration to confirm.
            if (!currentLiked) {
                haptics.toggled(true)
                currentOnLike(currentTrack)
            }
        }
    }
}

/**
 * The cover's taps: one shows the lyrics, two like, a long press opens speed and pitch.
 *
 * Adding the double tap means a single tap now waits out the double-tap timeout before it fires —
 * the detector cannot know a tap was single until no second one came. That is the price of the
 * gesture, and the same one every app that likes on a double tap pays. Drags are untouched: the
 * horizontal skip and the sheet's vertical drag consume the pointer once past slop, which cancels
 * any tap in progress, first or second.
 *
 * Keyed on [like], which is remembered for the screen's life, so the detector never restarts
 * mid-gesture; [onTap] and [onLongPress] must read their targets through updated state.
 */
internal fun Modifier.coverTapGestures(
    like: CoverDoubleTapLike,
    onTap: () -> Unit,
    onLongPress: () -> Unit
): Modifier = pointerInput(like) {
    detectTapGestures(
        onTap = { onTap() },
        onDoubleTap = like::onDoubleTap,
        onLongPress = { onLongPress() }
    )
}

/**
 * The heart itself, drawn over the cover where the tap landed: it springs in past its size on the
 * spatial spec, then fades out on the effects spec. Costs nothing between double taps.
 */
@Composable
internal fun CoverHeartOverlay(state: CoverDoubleTapLike, modifier: Modifier = Modifier) {
    val pop = state.pop ?: return
    Box(modifier = modifier.fillMaxSize()) {
        key(pop.id) {
            PoppingHeart(pop.at, onFinished = { if (state.pop?.id == pop.id) state.pop = null })
        }
    }
}

@Composable
private fun PoppingHeart(at: Offset, onFinished: () -> Unit) {
    val scale = remember { Animatable(StartScale) }
    val alpha = remember { Animatable(1f) }
    // Read in composition and held for the effect, which cannot reach the theme itself.
    val popIn = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fadeOut = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(Unit) {
        scale.animateTo(1f, popIn)
        alpha.animateTo(0f, fadeOut)
        onFinished()
    }

    Box(
        modifier = Modifier
            .offset {
                val half = HeartSize.toPx() / 2f
                IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt())
            }
            .size(HeartSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    ) {
        // A soft shadow under the heart, so it still reads over a white or very pale sleeve.
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.Black.copy(alpha = ShadowAlpha),
            modifier = Modifier.fillMaxSize().offset(y = 2.dp)
        )
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = OnCoverArt,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private val HeartSize = 96.dp

/** Small enough that the spring has room to carry it past full size before it settles. */
private const val StartScale = 0.3f

private const val ShadowAlpha = 0.3f
