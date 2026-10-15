package com.wander.android.core.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The gain [LoudnessNormaliser] applied to [trackId], for the debug overlay. */
data class AppliedLoudness(val trackId: String, val decision: LoudnessDecision)

/**
 * Hands the applied gain from the playback service to the debug overlay. Both run in the app's
 * one process, so a singleton is the whole channel — the same way `StreamResolver` hands over
 * what it resolved.
 */
@Singleton
class LoudnessReadout @Inject constructor() {

    private val _applied = MutableStateFlow<AppliedLoudness?>(null)
    val applied: StateFlow<AppliedLoudness?> = _applied.asStateFlow()

    internal fun publish(applied: AppliedLoudness) {
        _applied.value = applied
    }
}
