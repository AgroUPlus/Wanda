package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.PlaybackMediaType
import com.wander.android.data.model.UnifiedTrack

/**
 * What the song/video toggle shows for the playing track.
 *
 * [playing] is the form that is playing, or null while that is still being worked out — nothing is
 * highlighted then, rather than guessing "Song" and flipping a moment later for a video.
 * [alternative] is the matching other form once one is found, null while it is being looked for or
 * when none exists. The other side of the toggle is only enabled when [alternative] is set.
 */
internal class MediaToggleState(val playing: PlaybackMediaType?, val alternative: UnifiedTrack?)

private const val DISABLED_ALPHA = 0.38f
private const val IDLE_TEXT_ALPHA = 0.72f
private const val PILL_ALPHA = 0.2f
private const val SCRIM_ALPHA = 0.28f

/**
 * [MediaTypeToggle] that fades and expands in under the source name, and fades and collapses out
 * with the same springs when it goes away. The last state is held while it leaves, so the pill
 * does not blank out before it has finished shrinking.
 */
@Composable
internal fun AnimatedMediaToggle(state: MediaToggleState?, onSwap: () -> Unit) {
    val lastState = remember { mutableStateOf(state) }
    if (state != null) lastState.value = state
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(effects) + expandVertically(spatial),
        exit = fadeOut(effects) + shrinkVertically(spatial)
    ) {
        lastState.value?.let { shown ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                MediaTypeToggle(shown, onSwap)
            }
        }
    }
}

/**
 * A two-option pill whose highlight slides between Song and Video. Both options share the width of
 * the wider label, so the highlight is exactly half the track and only its offset has to animate.
 *
 * The whole pill is one button: a tap anywhere on it switches to the other form, so there is no
 * need to aim for the unselected label. It has no press ripple; the sliding highlight is the
 * feedback.
 */
@Composable
internal fun MediaTypeToggle(state: MediaToggleState, onSwap: () -> Unit) {
    val index = state.playing?.ordinal
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val position = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    // First resolution jumps straight to the playing form; only later changes slide.
    LaunchedEffect(index) {
        when {
            index == null -> placed = false
            !placed -> { position.snapTo(index.toFloat()); placed = true }
            else -> position.animateTo(index.toFloat(), spatial)
        }
    }
    val pillAlpha by animateFloatAsState(
        targetValue = if (index == null) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "MediaTogglePillAlpha"
    )

    val canSwap = state.playing != null && state.alternative != null
    Surface(
        color = Color.Black.copy(alpha = SCRIM_ALPHA),
        shape = CircleShape,
        contentColor = Color.White,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = canSwap,
            role = Role.Switch,
            onClick = onSwap
        )
    ) {
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(4.dp)
                .drawBehind {
                    val half = size.width / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = PILL_ALPHA * pillAlpha),
                        topLeft = Offset(position.value * half, 0f),
                        size = Size(half, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        ) {
            Option(PlaybackMediaType.SONG, state.playing, canSwap, Modifier.weight(1f))
            Option(PlaybackMediaType.VIDEO, state.playing, canSwap, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Option(
    type: PlaybackMediaType,
    playing: PlaybackMediaType?,
    canSwap: Boolean,
    modifier: Modifier
) {
    val selected = type == playing
    // The unselected side reads as unavailable until its counterpart has been found.
    val dimmed = playing == null || (!selected && !canSwap)
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) DISABLED_ALPHA else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "MediaToggleOptionAlpha"
    )
    val textColor by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (selected) 1f else IDLE_TEXT_ALPHA),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "MediaToggleTextColor"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(alpha)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(if (type == PlaybackMediaType.SONG) R.string.media_type_song else R.string.media_type_video),
            style = MaterialTheme.typography.labelMedium,
            // One weight for both states: bolding the selected label would resize it mid-slide.
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
