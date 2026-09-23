package com.wander.android.data.model

/** Where a listener stands with one episode. Derived from saved progress, never stored. */
enum class EpisodeState { IN_PROGRESS, UNPLAYED, FINISHED }

/**
 * An episode with its listening state.
 *
 * [fraction] is how much has been heard, 0..1 — 0 when unplayed or the duration is unknown, and
 * 1 once finished, so a progress bar never shows a sliver on something already done.
 */
data class EpisodeItem(
    val track: UnifiedTrack,
    val state: EpisodeState,
    val fraction: Float
)
