package com.wander.android.ui.screens.player

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalViewConfiguration
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether the click a skip button is about to fire ends a hold, and so must not also skip. */
internal class HoldToSeek {
    internal var held: Boolean = false

    /** True, once, if the click arriving now ends a hold. */
    fun consumeClick(): Boolean = held.also { held = false }
}

/**
 * Press-and-hold on a skip button scrubs instead of skipping: forward on next, back on previous,
 * the way a CD player's track buttons always did.
 *
 * Built on the button's own [InteractionSource] rather than a second gesture detector, because the
 * button's `clickable` already owns the pointer: stacking a `pointerInput` on it would have two
 * handlers racing for the same press. Here the press is only *watched*; once it has been held past
 * the platform's long-press timeout the stepping starts, and [HoldToSeek.consumeClick] lets the
 * button swallow the click its release fires, so a hold never also skips.
 *
 * @param direction +1 to fast-forward, -1 to rewind.
 * @param enabled read as each press starts — false while following a jam, for live streams and for
 *   anything the player says it cannot seek in. A plain tap still skips either way.
 */
@Composable
internal fun rememberHoldToSeek(
    interaction: InteractionSource,
    connection: PlayerConnection,
    direction: Int,
    enabled: Boolean
): HoldToSeek {
    val hold = remember { HoldToSeek() }
    val haptics = rememberHaptics()
    val timeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val currentEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(interaction, connection, direction) {
        var stepping: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    // A fresh press starts clean, so a hold that ended in a cancel rather than a
                    // click cannot leave the next ordinary tap swallowed.
                    hold.held = false
                    stepping?.cancel()
                    if (currentEnabled && !connection.isFollowing) {
                        stepping = launch {
                            delay(timeoutMs)
                            hold.held = true
                            haptics.heldDown()
                            var tick = 0
                            while (connection.holdSeekStep(direction * HoldSeekSteps.stepMs(tick++))) {
                                delay(HoldSeekSteps.INTERVAL_MS)
                            }
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    stepping?.cancel()
                    stepping = null
                }
            }
        }
    }
    return hold
}

/**
 * One beat of a held skip: moves the playhead by [deltaMs] within the track, and says whether there
 * is any further to go.
 *
 * Forward stops [HoldSeekSteps.END_MARGIN_MS] short of the end rather than on it: landing on the
 * last frame ends the track, the player moves on, and a finger still down would go on scanning the
 * *next* song — a hold that turned into a skip.
 */
private fun PlayerConnection.holdSeekStep(deltaMs: Long): Boolean {
    val position = currentPositionMs() ?: return false
    val durationMs = state.value.durationMs
    val end = if (durationMs > 0L) (durationMs - HoldSeekSteps.END_MARGIN_MS).coerceAtLeast(0L) else Long.MAX_VALUE
    val target = (position + deltaMs).coerceIn(0L, end)
    if (target != position) seekTo(target)
    return target in 1 until end
}

/** The pace of a held skip button: ~10 s per beat, growing the longer the finger stays down. */
internal object HoldSeekSteps {
    const val INTERVAL_MS = 350L
    const val END_MARGIN_MS = 1_000L
    private const val BASE_STEP_MS = 10_000L

    /** Beats at each pace before the next: roughly two seconds of holding. */
    private const val BEATS_PER_GEAR = 6

    /** Four times the base at most — 40 s a beat — so a long hold is fast but still aimable. */
    private const val MAX_GEAR = 4

    /** How far beat number [tick] (from 0) moves the playhead, before direction. */
    fun stepMs(tick: Int): Long = BASE_STEP_MS * (1 + tick / BEATS_PER_GEAR).coerceAtMost(MAX_GEAR)
}
