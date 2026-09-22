package com.wander.android.data.sources.agro

import androidx.compose.runtime.Immutable

/**
 * One account's year, as Agro computes it across every device on it.
 *
 * The fleet-wide half of Agro Replay. The same shape is produced locally from Room when no server
 * is paired — see `ReplayComputer` — so the two paths can feed one set of cards.
 *
 * Every list may be empty and every nullable may be null: a year with no listening in it is a
 * normal answer, and the cards that have nothing to draw are dropped rather than drawn with zeros.
 */
@Immutable
internal data class AgroWrapped(
    val year: Int,
    val totalMinutes: Long,
    val totalPlays: Long,
    val topArtists: List<StatEntry>,
    val topTracks: List<StatEntry>,
    val topAlbums: List<StatEntry>,
    val topGenres: List<StatEntry>,
    /** The peak hour in the listener's own offset, 0–23. Null when there were no plays. */
    val topHourLocal: Int?,
    /** The longest run of *consecutive* days with a play. */
    val longestStreakDays: Int,
    /** Days with any play at all, which is usually a much larger number than the streak. */
    val activeDaysCount: Int,
    val newArtistsCount: Int,
    /** Distinct artists. [topArtists] is a top ten, so its size says nothing. */
    val totalArtists: Int,
    /** Plays per calendar month, January first. Always twelve entries. */
    val byMonth: List<Long>,
    /** Plays per hour of the local day, index 0 = midnight. Always twenty-four entries. */
    val byHour: List<Long>,
    /** Plays per device. Only a paired server can answer this; locally there is one device. */
    val byDevice: List<StatEntry>,
    val firstPlay: String?,
    val lastPlay: String?
)

/**
 * Where this account's year sits among the other accounts on the same Agro server.
 *
 * [suppressed] is the normal answer on a household server, not an error: below a handful of
 * listeners a ranking describes the other people on it rather than a crowd, so the server declines
 * to compute one. A suppressed standing carries zeros in every other field, and the card that
 * would have shown it is dropped — never drawn as "top 100%".
 */
@Immutable
internal data class AgroChartStanding(
    val percentile: Int,
    val cohortSize: Int,
    val minutes: Int,
    val suppressed: Boolean
)
