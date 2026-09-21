package com.wander.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
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
    isSeekable: Boolean = true,
    inlineLabels: Boolean = false
) {
    val position by rememberPlaybackPosition(playerConnection, intervalMs = 250L)
    PlayerSeekBarInternal(
        positionMs = position.positionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier,
        isLive = isLive,
        isPlaying = isPlaying,
        isSeekable = isSeekable,
        inlineLabels = inlineLabels
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
    isSeekable: Boolean = true,
    inlineLabels: Boolean = false
) {
    PlayerSeekBarInternal(
        positionMs, durationMs, onSeek, modifier, isLive, isPlaying, isSeekable, inlineLabels
    )
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
    isSeekable: Boolean = true,
    /**
     * Put the two times either side of the track instead of under it.
     *
     * What the lyrics screen's pill wants: one short row rather than a two-line block, which keeps
     * the bar clear of the words behind it. The track gives up the width the labels take.
     */
    inlineLabels: Boolean = false
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

    // Keyed on `isPlaying` alone, not `isPlaying && durationMs > 0L`. `durationMs` can glitch to 0
    // transiently around track transitions, and a compound key flips on that glitch too — cancelling
    // and restarting this animation independently of any real pause, which could strand `amplitude`
    // partway to its target. See MiniPlayer.kt's PlaybackProgressBar for the same pattern and the
    // fuller rationale for deriving mount state from the animated value rather than a separate flag.
    val amplitude = remember { Animatable(if (isPlaying) 1f else 0f) }
    val amplitudeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(isPlaying) { amplitude.animateTo(if (isPlaying) 1f else 0f, amplitudeSpec) }
    val showWavy = isPlaying || amplitude.value > 0f

    val isScrubbing = scrubbing >= 0f
    // A dot, not a bar. One diameter rather than a width and a height: a tall thin thumb read as a
    // divider cutting the track in two, and the wave it sits on is already the thing with a shape.
    // It still grows under a finger — that is what says the drag has been taken.
    val thumbSize by animateDpAsState(
        targetValue = if (isScrubbing) ScrubbingThumbSize else RestingThumbSize,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "thumbSize"
    )

    // The position, not the fraction multiplied back out by the duration.
    //
    // Those agree whenever the duration is known and disagree completely when it is not: an
    // unknown duration pins `fraction` at zero, so this read `0:00` for a whole track whose
    // position the player was reporting correctly the entire time. While a finger is on the thumb
    // the fraction is what the user is choosing, and that is the one case where it leads.
    val positionLabel = formatTime(
        if (scrubbing >= 0f) (scrubbing * durationMs).toLong() else positionMs
    )
    val durationLabel = if (durationMs > 0L) formatTime(durationMs) else "--:--"

    // Hoisted so both layouts below place the same slider rather than each declaring its own.
    val slider: @Composable () -> Unit = {
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
                    modifier = Modifier.size(ThumbSlotSize),
                    contentAlignment = Alignment.Center
                ) {
                    ScrubTooltip(
                        visible = isScrubbing,
                        text = formatTime((fraction * durationMs).toLong())
                    )

                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            },
            track = { sliderState ->
                if (showWavy) {
                    LinearWavyProgressIndicator(
                        progress = { sliderState.value },
                        amplitude = { amplitude.value },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { sliderState.value },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    if (inlineLabels) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            val inlineColor = LocalContentColor.current
            TimeLabel(positionLabel, color = inlineColor)
            Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { slider() }
            TimeLabel(durationLabel, color = inlineColor)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        slider()
        // Inset to the track's own ends, not the slider's. A `Slider` reserves half a thumb-width
        // of padding at each side so the thumb can reach 0% and 100% without being clipped, so a
        // label row at full width starts and ends *outside* the bar it is labelling — which is
        // exactly how the position and duration came to hang off either edge.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = TrackInset)) {
            TimeLabel(positionLabel, modifier = Modifier.weight(1f))
            TimeLabel(durationLabel)
        }
    }
}

/**
 * The height a slider plus its label row occupies, so swapping one for the chip does not move
 * everything around it. Material's slider is 48.dp of touch target; the labels add the rest.
 */
private val LiveRowHeight = 68.dp

/**
 * The box the thumb is laid out in, and the dot drawn inside it.
 *
 * The slot is sized for the *largest* the dot ever gets, so growing it under a finger does not
 * change the slider's own measurement and shift the track beneath it.
 */
private val ThumbSlotSize = 24.dp
private val RestingThumbSize = 14.dp
private val ScrubbingThumbSize = 20.dp

/** Half the thumb slot — the padding the slider keeps clear at each end of the track. */
private val TrackInset = ThumbSlotSize / 2
