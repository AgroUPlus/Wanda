package com.wander.android.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberPressMorphShape

/** Resting and pressed shapes. A dial at rest, a circle while held. */
private val FabResting = MaterialShapes.Cookie9Sided
private val FabPressed = MaterialShapes.Circle

/** Where the button actually sits, so it grows out of its own corner rather than its centre. */
private val CornerOrigin = TransformOrigin(0f, 1f)

/** A quarter turn is enough to read as a dial being tuned in; more reads as a spin. */
private const val EnterSpin = -90f
private const val ExitSpin = 45f

/**
 * "Start radio" — a station out of nothing, in one press.
 *
 * Everything else on Home asks the user to choose something first: a shelf, a mix, a track. This
 * is for the times when choosing is itself the friction, which for a music player is most times.
 *
 * Bottom-left rather than the usual bottom-right: the right-hand side of the bottom edge is where
 * the thumb rests over the navigation bar and the mini player's controls, and a button that
 * starts playing something over the top of them is a button that gets pressed by accident.
 *
 * Icon only, and small. It started as an extended FAB with a label and it dominated the corner of
 * a screen whose whole job is showing artwork — the icon says it on its own, and a pill that size
 * competes with the content rather than sitting beside it.
 *
 * Hidden while the player is anywhere but docked, and while you are anywhere but Home. A button
 * pinned over a full-screen player is in the way of the player's controls, and one pinned over the
 * library belongs to a screen you are not on.
 *
 * Both conditions arrive as [visible] rather than as an `if` around the call, and that is the
 * point: an `if` drops the button out of composition, which is a cut, not an exit. The route used
 * to be exactly such an `if`, so opening the library made the button vanish between frames while
 * opening the player made it wind away — the same button leaving two different ways depending on
 * where you were going. Now it always leaves the way it arrived: winding back out of its corner,
 * spinning as it goes, which is what a tuning dial does and what nothing else on Home does.
 *
 * While the station is being assembled the icon pulses rather than swapping in a spinner — the
 * press has visibly done something, and the button keeps its size so nothing shifts underneath
 * the finger.
 */
@Composable
internal fun InstantRadioFab(
    isStarting: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The same morph every other primary control in the app wears. A stock rounded-corner FAB was
    // the one shape on Home that did not belong to the same family as the buttons on the album and
    // artist pages — and a dial is exactly the thing that should look like it can be turned.
    val shape = rememberPressMorphShape(FabResting, FabPressed, pressed)
    val pulseTransition = rememberInfiniteTransition(label = "radio-fab")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "radio-pulse"
    )

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        // Slow spatial in, fast out. Arriving is the button announcing itself; leaving is it
        // getting out of the way of something the user has already started.
        enter = scaleIn(motion.slowSpatialSpec(), transformOrigin = CornerOrigin) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec(), transformOrigin = CornerOrigin) +
            fadeOut(motion.fastEffectsSpec()),
        modifier = modifier
    ) {
        // Driven off the same transition as the scale, so the spin cannot run long or short of it.
        val spin by transition.animateFloat(
            transitionSpec = { motion.slowSpatialSpec() },
            label = "radio-spin"
        ) { state ->
            when (state) {
                EnterExitState.Visible -> 0f
                EnterExitState.PreEnter -> EnterSpin
                EnterExitState.PostExit -> ExitSpin
            }
        }

        SmallFloatingActionButton(
            onClick = onClick,
            shape = shape,
            interactionSource = interaction,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            // Flat, and this is load-bearing rather than a style choice. A drop shadow is
            // tessellated from the outline, and the shape above is concave — Skia's concave
            // shadow tessellator on a nine-lobed star is slow enough to wedge the render thread
            // outright, which showed up as the whole app freezing on launch rather than as a
            // dropped frame. The shaped buttons on the album and artist pages never hit it
            // because icon buttons have no elevation to begin with.
            //
            // Nothing is lost: this could never out-stack the docked player — that is drawn after
            // the whole nav host and always wins — so the two are kept from overlapping by the
            // caller's bottom offset rather than by elevation, and the container colour is what
            // separates the button from the artwork behind it. See `HomeScreen`.
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            ),
            modifier = Modifier.graphicsLayer {
                rotationZ = spin
                transformOrigin = CornerOrigin
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = if (isStarting) "Starting radio" else "Start radio",
                modifier = Modifier.graphicsLayer { alpha = if (isStarting) pulse else 1f }
            )
        }
    }
}
