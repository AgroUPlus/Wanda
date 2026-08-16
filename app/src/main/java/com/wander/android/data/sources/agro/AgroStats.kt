package com.wander.android.data.sources.agro

import androidx.compose.runtime.Immutable

/** How far back a statistics query reaches. The names are what the server expects on the wire. */
enum class StatsPeriod(val wireName: String, val label: String) {
    WEEK("WEEK", "Week"),
    MONTH("MONTH", "Month"),
    YEAR("YEAR", "Year"),
    ALL("ALL", "All time")
}

/** A named total: an artist, an album, a track, a device. */
@Immutable
data class StatEntry(val name: String, val value: Long)

/**
 * Listening figures as Agro reports them.
 *
 * `secondsToday` is the last 24 hours rather than since midnight: a fleet has no single timezone,
 * so there is no one midnight to count from.
 */
@Immutable
data class AgroStats(
    val secondsToday: Long,
    val secondsWeek: Long,
    val secondsTotal: Long,
    val playCount: Long,
    val streakDays: Int,
    val topArtists: List<StatEntry>,
    val topAlbums: List<StatEntry>,
    val topTracks: List<StatEntry>,
    /** Seconds per day for the last fourteen days, oldest first. */
    val byDay: List<Long>,
    /** Seconds per hour of the day, UTC, index 0 = midnight. */
    val byHour: List<Long>,
    /** Seconds per device, most-listened first. Empty when Agro is not the source. */
    val byDevice: List<StatEntry>
)
