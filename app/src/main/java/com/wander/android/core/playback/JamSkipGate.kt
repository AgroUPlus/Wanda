package com.wander.android.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns "next" into a vote while this device is in a jam.
 *
 * The room's server decides when a track ends, so a local skip could only ever put this device out
 * of step with everyone else — and with a jam's one-track local queue it did nothing at all. Every
 * skip control routes through here instead: the in-app buttons and gestures via `PlayerConnection`,
 * and the notification, lockscreen and headset buttons via the session player in `PlaybackService`.
 */
@Singleton
class JamSkipGate @Inject constructor() {

    @Volatile
    private var voter: (() -> Unit)? = null

    private val _active = MutableStateFlow(false)
    /** Whether a skip is currently a vote. The service watches it to offer "next" on one track. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Installs the vote for the jam's lifetime; null when the jam ends. */
    fun setVoter(vote: (() -> Unit)?) {
        voter = vote
        _active.value = vote != null
    }

    /** Casts a vote if in a jam. Returns false when there is none, so the caller skips normally. */
    fun tryVote(): Boolean {
        val vote = voter ?: return false
        vote()
        return true
    }
}
