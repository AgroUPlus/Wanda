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

    fun stopAtEndOfItem() {
        _state.value = SleepTimerState.EndOfItem
    }

    fun cancel() {
        _state.value = SleepTimerState.Off
    }
}
