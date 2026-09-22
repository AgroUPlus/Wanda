package com.wander.android.data.replay

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A named total: an artist, an album, a track, a genre, a device. */
@Serializable
@Immutable
data class ReplayEntry(val name: String, val value: Long)

/**
 * Everything Agro Replay knows about one year.
 *
 * Serializable because a saved recap outlives the plays behind it: once the user has accepted the
 * tidy-up at the end of the story, this object *is* the year. See `ReplayArchivist`.
 *
 * The nullable fields follow the same convention as `ListeningReport`: a figure that nothing can
 * honestly answer is absent, and the card that would have shown it is dropped. None of them is
 * ever filled with a zero to keep a card on screen — a recap that invents numbers is worse than a
 * shorter one.
 */
@Serializable
@Immutable
data class ReplayReport(
    val year: Int,
    /** True when the figures cover every device on the Agro account rather than only this one. */
    val isFleetWide: Boolean,
    val totalMinutes: Long,
    val totalPlays: Long,
    val topArtists: List<ReplayEntry>,
    val topTracks: List<ReplayEntry>,
    val topAlbums: List<ReplayEntry>,
    val topGenres: List<ReplayEntry>,
    /** The peak listening hour, 0–23, in the listener's own time. Null when nothing was played. */
    val topHourLocal: Int?,
    /** The longest run of consecutive days with a play. */
    val longestStreakDays: Int,
    /** Days with any play at all — a different, usually much larger, figure than the streak. */
    val activeDaysCount: Int,
    /** Artists heard this year that had never been played before it. */
    val newArtistsCount: Int,
    /** Distinct artists. [topArtists] is a top ten, so its size says nothing. */
    val totalArtists: Int,
    /** Plays per calendar month, January first. Always twelve entries. */
    val byMonth: List<Long>,
    /** Plays per hour of the local day, index 0 = midnight. Always twenty-four entries. */
    val byHour: List<Long>,
    /** Plays per device. Empty without a paired server, where there is only ever one device. */
    val byDevice: List<ReplayEntry>,
    /** Where this year sat among the server's other listeners. Null when unpaired or suppressed. */
    val chartPercentile: Int? = null,
    /** How many accounts that percentile was measured against. */
    val chartCohortSize: Int = 0,
    /** The circle's shared year. Null when unpaired, or when no friend has opened their stats. */
    val circle: ReplayCircle? = null,
    /** When this report was computed, for a saved recap to date itself by. */
    val generatedAtMillis: Long = 0L
) {
    /** Nothing was played at all. The story opens and closes on a single card. */
    val isEmpty: Boolean get() = totalPlays == 0L
}

/**
 * What the circle did, for the friends card.
 *
 * A flattened copy of the server's recap rather than a reference to it: this has to survive being
 * written to disk and read back years later, when the friendship, the server, or both may be gone.
 */
@Serializable
@Immutable
data class ReplayCircle(
    /** You, plus the friends who have opened their statistics. Nobody else is in it. */
    val members: List<String>,
    val anthemTitle: String?,
    val anthemArtist: String?,
    val anthemPlays: Long,
    /** Who reached the circle's top tracks before anyone else, and how often. */
    val trendsetter: String?,
    val trendsetterFirsts: Long,
    /** The friend whose taste overlapped this account's most, and by how much. */
    val closestFriend: String?,
    val closestScore: Int
) {
    /**
     * Whether this is worth a card.
     *
     * A circle of one is the normal answer for somebody with no friends on the server, and the
     * server returns it rather than an error — but "your circle's anthem is your own top song" is
     * not a fact about a circle, so the card is dropped instead of shown.
     */
    val isWorthShowing: Boolean get() = members.size > 1
}
