package com.wander.android.data.replay

import com.wander.android.core.database.dao.PlayedTrack
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Builds a year's recap out of this device's own plays.
 *
 * **A deliberate port of `compute_wrapped` in Agro's `src/stats_wrapped.rs`, and it has to stay in
 * lockstep with it.** The same recap is computed in two places — here when no server is paired,
 * there when one is — and a device that showed a different streak or a different top artist after
 * pairing would read as data loss rather than as a wider view. Same buckets, same tiebreaks, same
 * definition of what makes an artist new.
 *
 * Pure: no Room, no Android, no clock of its own. Everything it needs is an argument, which is what
 * lets `ReplayComputerTest` assert the same answers the Rust tests assert.
 *
 * ## What this device cannot know
 *
 * Room records *when* a play happened, never for how long, so listening time is reconstructed as
 * "the track's full duration, once per play" — what the play was worth if it ran to the end. The
 * same estimate `StatsRepository.localReport` already makes, and the screen says it is one.
 */
internal object ReplayComputer {

    /**
     * @param plays every play inside the recap year, in any order.
     * @param priorArtists the artists played *before* the year, for [ReplayReport.newArtistsCount].
     */
    fun compute(
        year: Int,
        plays: List<PlayedTrack>,
        priorArtists: Set<String>,
        zone: ZoneId,
        generatedAtMillis: Long
    ): ReplayReport {
        val artists = mutableMapOf<String, Long>()
        val tracks = mutableMapOf<String, Long>()
        val albums = mutableMapOf<String, Long>()
        val genres = mutableMapOf<String, Long>()
        val byMonth = LongArray(MONTHS)
        val byHour = LongArray(HOURS)
        val activeDays = mutableSetOf<Long>()
        var totalMs = 0L

        for (play in plays) {
            totalMs += play.durationMs.coerceAtLeast(0L)
            val at = play.playedAt.atZone(zone)

            artists.increment(play.artist)
            tracks.increment(joinName(play.title, play.artist))
            albums.increment(joinName(play.album ?: UNKNOWN_ALBUM, play.artist))
            play.genre?.takeIf { it.isNotBlank() }?.let { genres.increment(it) }

            byMonth[at.monthValue - 1]++
            byHour[at.hour]++
            activeDays.add(at.toLocalDate().toEpochDay())
        }

        val periodArtists = artists.keys
        return ReplayReport(
            year = year,
            isFleetWide = false,
            totalMinutes = totalMs / MILLIS_PER_MINUTE,
            totalPlays = plays.size.toLong(),
            topArtists = artists.rank(TOP_N),
            topTracks = tracks.rank(TOP_N),
            topAlbums = albums.rank(TOP_N),
            topGenres = genres.rank(TOP_N),
            topHourLocal = byHour.peak(),
            longestStreakDays = longestStreak(activeDays),
            activeDaysCount = activeDays.size,
            newArtistsCount = periodArtists.count { it !in priorArtists },
            totalArtists = periodArtists.size,
            byMonth = byMonth.toList(),
            byHour = byHour.toList(),
            // One device is not a breakdown. The card exists for a fleet and is dropped here.
            byDevice = emptyList(),
            generatedAtMillis = generatedAtMillis
        )
    }

    /**
     * The longest run of consecutive days, which is what a streak is.
     *
     * Not the number of days with a play in them: for somebody who listens most days that figure is
     * near 365 and would read as a year-long streak nobody had. [ReplayReport.activeDaysCount]
     * carries that other number, and it is a card of its own.
     */
    fun longestStreak(activeDays: Set<Long>): Int {
        if (activeDays.isEmpty()) return 0
        val ordered = activeDays.sorted()
        var best = 1
        var run = 1
        for (index in 1 until ordered.size) {
            run = if (ordered[index] == ordered[index - 1] + 1) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    /**
     * `Title — Artist`, the one name the server also uses for a ranked track or album.
     *
     * Kept identical so a recap computed here and one computed by Agro can be told apart only by
     * the numbers in it, never by how the entries are spelled — and so the same `lastIndexOf` split
     * `StatsRepository` already uses works on both.
     */
    private fun joinName(what: String, artist: String): String = "$what$NAME_SEPARATOR$artist"

    private fun MutableMap<String, Long>.increment(key: String) {
        this[key] = (this[key] ?: 0L) + 1L
    }

    /** Most played first, with the name as a tiebreak so equal counts do not jitter. */
    private fun Map<String, Long>.rank(limit: Int): List<ReplayEntry> =
        entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }
            .thenBy { it.key })
            .take(limit)
            .map { ReplayEntry(it.key, it.value) }

    /** The busiest hour, or null when nothing was played — never hour zero by default. */
    private fun LongArray.peak(): Int? =
        withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index

    private fun Long.atZone(zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(this).atZone(zone)

    private const val MONTHS = 12
    private const val HOURS = 24
    private const val TOP_N = 10
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val UNKNOWN_ALBUM = "Unknown Album"

    /** The em dash Agro joins names with. */
    const val NAME_SEPARATOR = " — "
}
