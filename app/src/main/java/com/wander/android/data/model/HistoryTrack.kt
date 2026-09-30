package com.wander.android.data.model

import androidx.compose.runtime.Immutable

/**
 * One play as the History screen shows it: a track plus the moment it happened.
 *
 * [UnifiedTrack] alone cannot carry this — a track is played many times, and the History screen's
 * whole point is showing each of those times, not the track's latest [UnifiedTrack.lastPlayedTimestamp].
 * [historyId] is the paging key, since a track's own id repeats once per play.
 */
@Immutable
data class HistoryTrack(
    val historyId: Long,
    val playedAt: Long,
    val track: UnifiedTrack
)
