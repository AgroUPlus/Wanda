package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.ui.components.LiveChip
import com.wander.android.ui.components.rememberHaptics
import java.util.Locale

/**
 * Expressive wavy slider. While the user drags, the local value wins so the thumb tracks the
 * finger instead of fighting the periodic position updates.
 *
 * The wave is the playing state, not decoration: it runs while the track does and flattens when it
 * stops, so a glance at the bar says whether anything is coming out of the speaker. It also
 * flattens under a finger — a scrub wants a straight edge to aim the thumb along, and a travelling
 * wave under it reads as the position still moving while you are trying to place it.
 *
 * The docked strip has had [androidx.compose.material3.LinearWavyProgressIndicator] since it was
 * written; this is the same treatment for the player you get when you open it, which was still a
 * flat track despite what this comment has always claimed.
 */
@Composable
fun PlayerSeekBar(
    playerConnection: PlayerConnection,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    isPlaying: Boolean = true,
    isSeekable: Boolean = true
) {
    val position by rememberPlaybackPosition(playerConnection, intervalMs = 250L)
    PlayerSeekBarInternal(
        positionMs = position.positionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier,
        isLive = isLive,
        isPlaying = isPlaying,
        isSeekable = isSeekable
    )
}

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    isPlaying: Boolean = true,
    isSeekable: Boolean = true
) {
    PlayerSeekBarInternal(positionMs, durationMs, onSeek, modifier, isLive, isPlaying, isSeekable)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBarInternal(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    isPlaying: Boolean = true,
    isSeekable: Boolean = true
) {
    var scrubbing by remember { mutableFloatStateOf(-1f) }
    var lastTickInterval by remember { mutableIntStateOf(-1) }
    val haptics = rememberHaptics()
    val fraction = if (scrubbing >= 0f) {
        scrubbing
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    if (isLive) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(LiveRowHeight),
            contentAlignment = Alignment.Center
        ) {
            LiveChip()
        }
        return
    }

    val waving = isPlaying && durationMs > 0L
    val amplitude = remember { Animatable(if (waving) 1f else 0f) }
    val amplitudeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(waving) { amplitude.animateTo(if (waving) 1f else 0f, amplitudeSpec) }

    val isScrubbing = scrubbing >= 0f
    val thumbSpatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val thumbWidth by animateDpAsState(
        targetValue = if (isScrubbing) 8.dp else 4.dp,
        animationSpec = thumbSpatial,
        label = "thumbWidth"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isScrubbing) 28.dp else 16.dp,
        animationSpec = thumbSpatial,
        label = "thumbHeight"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = {
                scrubbing = it
                if (durationMs > 0L) {
                    val currentSec = ((it * durationMs) / 1000).toInt()
                    val interval = currentSec / 15
                    if (interval != lastTickInterval) {
                        lastTickInterval = interval
                        haptics.tick()
                    }
                }
            },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    haptics.settled()
                    onSeek((scrubbing * durationMs).toLong())
                }
                scrubbing = -1f
                lastTickInterval = -1
            },
            enabled = durationMs > 0L && isSeekable,
            thumb = {
                Box(
                    modifier = Modifier.size(width = 16.dp, height = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScrubTooltip(
                        visible = isScrubbing,
                        text = formatTime((fraction * durationMs).toLong())
                    )

                    Box(
                        modifier = Modifier
                            .size(width = thumbWidth, height = thumbHeight)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            },
            track = { sliderState ->
                LinearWavyProgressIndicator(
                    progress = { sliderState.value },
                    amplitude = { amplitude.value },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                // The position, not the fraction multiplied back out by the duration.
                //
                // Those agree whenever the duration is known and disagree completely when it is
                // not: an unknown duration pins `fraction` at zero, so this read `0:00` for a
                // whole track whose position the player was reporting correctly the entire time.
                // While a finger is on the thumb the fraction is what the user is choosing, and
                // that is the one case where it leads.
                text = formatTime(
                    if (scrubbing >= 0f) (scrubbing * durationMs).toLong() else positionMs
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The time bubble that rides above the thumb while a finger is on it.
 *
 * A function of its own rather than written inline, and not for tidiness: inline, it sat lexically
 * inside the seek bar's `Column`, so `ColumnScope.AnimatedVisibility` won overload resolution — and
 * was then rejected, because the slider's `thumb` lambda is not a `ColumnScope`. Out here there is
 * no such receiver in scope and the ordinary overload is chosen. Forcing the receiver instead would
 * have compiled and animated the bubble as a column child, which is not what it is.
 */
@Composable
private fun ScrubTooltip(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f),
        modifier = Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints())
            layout(0, 0) {
                placeable.placeRelative(
                    x = -placeable.width / 2,
                    y = -placeable.height - 24.dp.roundToPx()
                )
            }
        }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * The height a slider plus its label row occupies, so swapping one for the chip does not move
 * everything around it. Material's slider is 48.dp of touch target; the labels add the rest.
 */
private val LiveRowHeight = 68.dp

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
