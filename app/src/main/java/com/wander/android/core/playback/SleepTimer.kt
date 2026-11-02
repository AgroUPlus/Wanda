package com.wander.android.core.playback

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Stops playback at [endsAtElapsedMs], on the [SystemClock.elapsedRealtime] clock. */
    data class Countdown(val endsAtElapsedMs: Long) : SleepTimerState

    /** Stops playback when the current episode or track ends. */
    data object EndOfItem : SleepTimerState
}

/**
 * What the sleep timer is set to. Only the request lives here — [SleepTimerEnforcer], inside
 * `PlaybackService`, is what acts on it, because only the service holds the `ExoPlayer` and only
 * the service is guaranteed to still be running when the screen has long been off.
 *
 * Elapsed-realtime rather than wall clock, so a timezone change or a clock sync mid-countdown
 * does not move the end.
 */
@Singleton
class SleepTimer @Inject constructor() {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun startCountdown(minutes: Int) {
        _state.value = SleepTimerState.Countdown(SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    /**
     * Pushes a running countdown back by [minutes]. Counted from now if the end has already been
     * reached — the fade is under way — so the extension always buys the full amount. Does nothing
     * unless a countdown is running.
     */
    fun extend(minutes: Int) {
        val current = _state.value as? SleepTimerState.Countdown ?: return
        _state.value = SleepTimerState.Countdown(
            extendedEnd(current.endsAtElapsedMs, SystemClock.elapsedRealtime(), minutes)
        )
    }

    fun stopAtEndOfItem() {
        _state.value = SleepTimerState.EndOfItem
    }

    fun cancel() {
        _state.value = SleepTimerState.Off
    }
}

/** Where a countdown ending at [endsAtMs] ends once [minutes] are added at [nowMs]. */
internal fun extendedEnd(endsAtMs: Long, nowMs: Long, minutes: Int): Long =
    maxOf(endsAtMs, nowMs) + minutes * 60_000L
