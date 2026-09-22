package com.wander.android.data.replay

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroFeedApi
import com.wander.android.data.sources.agro.AgroRecap
import com.wander.android.data.sources.agro.AgroReplayApi
import com.wander.android.data.sources.agro.AgroWrapped
import com.wander.android.data.sources.agro.StatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a year's recap comes from.
 *
 * The same two-source split as `StatsRepository`, and deliberately the same shape: Agro when a
 * server is paired, Room when none is. A second pattern for the same choice would be a second
 * thing to keep right.
 *
 * The difference between the two is not hidden. A fleet recap covers every device on the account
 * and can say how the year compared with the server's other listeners and with friends; a local one
 * covers this phone and says so on its first card. Neither pretends to be the other.
 */
@Singleton
internal class ReplayRepository @Inject constructor(
    private val replayApi: AgroReplayApi,
    private val feedApi: AgroFeedApi,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage
) {
    /** Whether the recap can cover more than this device. */
    val isFleetWide: Boolean get() = secureStorage.agroConfigured.value

    suspend fun report(
        year: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<ReplayReport> = withContext(Dispatchers.IO) {
        if (isFleetWide) {
            // No local fallback when the server fails. Quietly substituting this phone's smaller
            // year for the account's would look like listening had disappeared, with nothing on
            // screen to say the source had changed — the same rule `StatsRepository` follows.
            replayApi.wrapped(year, zone.offsetMinutes(year))
                .mapCatching { wrapped -> fleetReport(year, wrapped, zone) }
        } else {
            runCatching { localReport(year, zone) }
        }
    }

    /**
     * What Agro can say: every device, and how the year sat against other people's.
     *
     * The chart standing and the circle are fetched separately and are allowed to fail. Neither is
     * what the recap is *about*, and a friends card that could not load is a card that is not
     * shown — unlike the recap itself, whose failure fails the whole thing.
     */
    private suspend fun fleetReport(
        year: Int,
        wrapped: AgroWrapped,
        zone: ZoneId
    ): ReplayReport {
        val offset = zone.offsetMinutes(year)
        val standing = replayApi.chartStanding(year, offset).getOrNull()
        val circle = feedApi.recap(year = year, utcOffsetMinutes = offset)
            .getOrNull()
            ?.toReplayCircle(me = secureStorage.agroUsername)

        return ReplayReport(
            year = wrapped.year,
            isFleetWide = true,
            totalMinutes = wrapped.totalMinutes,
            totalPlays = wrapped.totalPlays,
            topArtists = wrapped.topArtists.toReplayEntries(),
            topTracks = wrapped.topTracks.toReplayEntries(),
            topAlbums = wrapped.topAlbums.toReplayEntries(),
            topGenres = wrapped.topGenres.toReplayEntries(),
            topHourLocal = wrapped.topHourLocal,
            longestStreakDays = wrapped.longestStreakDays,
            activeDaysCount = wrapped.activeDaysCount,
            newArtistsCount = wrapped.newArtistsCount,
            totalArtists = wrapped.totalArtists,
            byMonth = wrapped.byMonth,
            byHour = wrapped.byHour,
            byDevice = wrapped.byDevice.toReplayEntries(),
            // A suppressed standing carries zeros, and a zero percentile is a real answer for the
            // quietest listener on a server. Only a standing the server actually computed becomes
            // a card; everything else is absent.
            chartPercentile = standing?.takeIf { !it.suppressed }?.percentile,
            chartCohortSize = standing?.takeIf { !it.suppressed }?.cohortSize ?: 0,
            circle = circle?.takeIf { it.isWorthShowing },
            generatedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * What Room can say alone: this device, and nothing about anybody else.
     *
     * Two reads. The year itself, and the distinct artists from before it — the second is what
     * makes "artists you discovered" mean anything, and it is a list of names rather than a second
     * lifetime of plays.
     */
    private suspend fun localReport(year: Int, zone: ZoneId): ReplayReport {
        val bounds = yearBounds(year, zone)
        return ReplayComputer.compute(
            year = year,
            plays = historyDao.getPlaysBetween(bounds.first, bounds.second),
            priorArtists = historyDao.artistsPlayedBefore(bounds.first).toSet(),
            zone = zone,
            generatedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Half-open millisecond bounds of a calendar year, `[start, end)`.
     *
     * Half-open so this year and the next partition the history rather than both claiming midnight.
     */
    fun yearBounds(year: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /**
     * This zone's offset in the middle of [year], in minutes.
     *
     * Midsummer rather than now, so a recap read in January describes the year it is about. A zone
     * that changes offset within the year has no single answer — see the note in Agro's
     * `stats_wrapped`; the cost is up to an hour on the hour buckets, which moves the peak only
     * when two hours were already that close.
     */
    private fun ZoneId.offsetMinutes(year: Int): Int {
        val midYear = LocalDate.of(year, 7, 1).atStartOfDay(this).toInstant()
        return (rules.getOffset(midYear).totalSeconds / SECONDS_PER_MINUTE)
    }

    private fun List<StatEntry>.toReplayEntries(): List<ReplayEntry> =
        map { ReplayEntry(it.name, it.value) }

    /**
     * Flattens the server's circle recap into what a card needs and a saved recap can keep.
     *
     * The closest friend comes from the taste matrix the recap already carries, rather than a
     * `tasteMatch` call per member: the matrix is one round trip and the scores are the same ones.
     */
    private fun AgroRecap.toReplayCircle(me: String): ReplayCircle {
        val closest = matrix
            .filter { it.a == me || it.b == me }
            .maxByOrNull { it.score }

        return ReplayCircle(
            members = members,
            anthemTitle = anthem?.title,
            anthemArtist = anthem?.artist,
            anthemPlays = anthem?.plays ?: 0L,
            trendsetter = trendsetter?.username,
            trendsetterFirsts = trendsetter?.firsts ?: 0L,
            closestFriend = closest?.let { if (it.a == me) it.b else it.a },
            closestScore = closest?.score ?: 0
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
