package com.wander.android.data.repository

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.PlayedTrack
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.AgroStatsApi
import com.wander.android.data.sources.agro.StatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening figures, from Agro when there is an Agro and from this device when there is not.
 *
 * Agro wins when it is paired because it is the only place that can answer the question the user
 * is actually asking — what *I* listened to, not what this handset played. Without it the answer
 * falls back to what Room can say on its own, which is real but partial, and the screen says so
 * rather than presenting a device total as a personal one.
 *
 * What the two can answer differs, and [ListeningReport] carries that difference rather than
 * hiding it: only the local history can be sliced to an arbitrary [StatsWindow], so only the local
 * path can compare a window against the one before it or page backwards through them.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val statsApi: AgroStatsApi,
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage
) {
    /** Whether the figures can cover more than this device. */
    val isFleetWide: Boolean get() = secureStorage.agroConfigured.value

    suspend fun report(window: StatsWindow): Result<ListeningReport> = withContext(Dispatchers.IO) {
        if (isFleetWide) {
            // No local fallback on failure. Quietly substituting this device's smaller numbers for
            // the fleet's would look like listening had disappeared, with nothing on screen to say
            // the source had changed.
            statsApi.listeningStats(window.period).mapCatching { fleetReport(window, it) }
        } else {
            runCatching { localReport(window) }
        }
    }

    /**
     * What Agro can say.
     *
     * The server answers for a period, not for a window, so the offset is dropped and the report
     * is built with every comparative figure absent. The screen renders the tiles it has.
     */
    private suspend fun fleetReport(window: StatsWindow, stats: AgroStats): ListeningReport {
        val days = window.period.dayCount().coerceAtLeast(1)
        return ListeningReport(
            window = window.copy(offset = 0),
            stats = stats,
            isFleetWide = true,
            topSong = stats.topTracks.firstOrNull()?.let { entry -> topSongFromEntry(entry) },
            songsPlayed = Trend(stats.playCount),
            listenedSeconds = Trend(stats.secondsTotal),
            playsPerDay = Trend(stats.playCount / days),
            // Agro breaks the fleet's days down by seconds, not by plays, and there is no earlier
            // window to compare the heaviest one against.
            busiestDay = null,
            // `topArtists` is a top ten, not a count. Reporting its size would read as "you
            // listened to exactly ten artists" for every account that ever listened to eleven.
            artists = null
        )
    }

    /**
     * Splits `Title — Artist` back apart and finds a cover for it if this device knows the song.
     *
     * `lastIndexOf` rather than `indexOf`: a title containing an em dash is a real title, and an
     * artist containing one is rarer than a song called `Sunset — Reprise`.
     */
    private suspend fun topSongFromEntry(entry: StatEntry): TopSong {
        val split = entry.name.lastIndexOf(NAME_SEPARATOR)
        val title = if (split > 0) entry.name.take(split) else entry.name
        val artist = if (split > 0) entry.name.substring(split + NAME_SEPARATOR.length) else ""
        return TopSong(
            trackId = null,
            title = title,
            artist = artist,
            artworkUrl = if (artist.isBlank()) null else trackDao.artworkFor(title, artist),
            // Agro ranks its top tracks by play count, so there is no listening time to show.
            seconds = null
        )
    }

    /**
     * What Room can say alone.
     *
     * Room keeps a history of when plays happened, but not how long each one lasted — so seconds
     * are reconstructed as "the track's duration, once per play", which is what the play was worth
     * if it ran to the end. It is an estimate, and the screen labels it as this device only.
     *
     * The window before this one is read as well, and only for its totals. That second query is
     * what every `+15%` on the screen is made of; without it the figures are true but say nothing
     * about whether this was a heavy week or a quiet one.
     */
    private suspend fun localReport(window: StatsWindow): ListeningReport {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val bounds = window.bounds(now, zone)
        val plays = historyDao.getPlaysBetween(bounds.first, bounds.last)

        val previous = if (window.isPageable) {
            val earlier = window.earlier().bounds(now, zone)
            historyDao.getPlaysBetween(earlier.first, earlier.last)
        } else {
            null
        }

        val days = window.period.dayCount()
            .takeIf { it > 0 }
            ?: spannedDays(plays)
        val previousDays = window.period.dayCount().coerceAtLeast(1)

        val byDayPlays = plays.groupingBy { it.dayStart(zone) }.eachCount()
        val busiest = byDayPlays.maxByOrNull { it.value }
        val previousBusiest = previous
            ?.groupingBy { it.dayStart(zone) }
            ?.eachCount()
            ?.values
            ?.maxOrNull()
            ?.toLong()

        return ListeningReport(
            window = window,
            stats = localStats(plays, window, now, zone),
            isFleetWide = false,
            topSong = topSongOf(plays),
            songsPlayed = Trend.of(plays.size.toLong(), previous?.size?.toLong()),
            listenedSeconds = Trend.of(plays.seconds(), previous?.seconds()),
            playsPerDay = Trend.of(
                plays.size.toLong() / days.coerceAtLeast(1),
                previous?.let { it.size.toLong() / previousDays }
            ),
            busiestDay = busiest?.let {
                BusiestDay(it.key, Trend.of(it.value.toLong(), previousBusiest))
            },
            artists = Trend.of(
                plays.distinctArtists(),
                previous?.distinctArtists()
            )
        )
    }

    /** The charts and ranked lists, over the same window as everything above them. */
    private fun localStats(
        plays: List<PlayedTrack>,
        window: StatsWindow,
        now: Long,
        zone: ZoneId
    ): AgroStats {
        val dayAgo = now - TimeUnit.DAYS.toMillis(1)
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val end = window.bounds(now, zone).last

        val byDay = LongArray(DAY_BUCKETS)
        val byHour = LongArray(24)
        var secondsToday = 0L
        var secondsWeek = 0L
        val playedDays = mutableSetOf<Int>()

        plays.forEach { play ->
            val seconds = play.durationMs / 1000
            if (play.playedAt >= dayAgo) secondsToday += seconds
            if (play.playedAt >= weekAgo) secondsWeek += seconds

            // Counted back from the end of the window rather than from now, so paging backwards
            // moves the chart with the dates instead of emptying it.
            val daysBack = ((end - play.playedAt) / TimeUnit.DAYS.toMillis(1)).toInt()
            if (daysBack in 0 until DAY_BUCKETS) byDay[DAY_BUCKETS - 1 - daysBack] += seconds
            if (daysBack >= 0) playedDays += daysBack
            byHour[Instant.ofEpochMilli(play.playedAt).atZone(zone).hour] += seconds
        }

        var streak = 0
        while (streak in playedDays) streak++

        return AgroStats(
            secondsToday = secondsToday,
            secondsWeek = secondsWeek,
            secondsTotal = plays.seconds(),
            playCount = plays.size.toLong(),
            streakDays = streak,
            topArtists = plays.groupingBy { it.artist }.eachCount().toEntries(),
            topAlbums = plays
                .groupingBy { "${it.album ?: "Unknown Album"}$NAME_SEPARATOR${it.artist}" }
                .eachCount()
                .toEntries(),
            // Grouped by song rather than by row id: the same recording reached through two
            // sources is one song you played twice, not two songs you played once.
            topTracks = plays
                .groupingBy { "${it.title}$NAME_SEPARATOR${it.artist}" }
                .eachCount()
                .toEntries(),
            byDay = byDay.toList(),
            byHour = byHour.toList(),
            // Nothing to break down: without Agro this device is the only one there is.
            byDevice = emptyList()
        )
    }

    /** The song the window spent the most time on, with the cover it was played under. */
    private fun topSongOf(plays: List<PlayedTrack>): TopSong? = plays
        .groupBy { it.title.lowercase() to it.artist.lowercase() }
        .maxByOrNull { (_, rows) -> rows.sumOf { it.durationMs } }
        ?.value
        ?.let { rows ->
            val first = rows.first()
            TopSong(
                trackId = first.trackId,
                title = first.title,
                artist = first.artist,
                artworkUrl = rows.firstNotNullOfOrNull { it.artworkUrl },
                seconds = rows.sumOf { it.durationMs } / 1000
            )
        }
}

private const val DAY_BUCKETS = 14
private const val TOP_N = 10

/** How `Album — Artist` and `Title — Artist` are joined, and therefore how they are split. */
private const val NAME_SEPARATOR = " — "

private fun PlayedTrack.dayStart(zone: ZoneId): Long =
    Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

private fun List<PlayedTrack>.seconds(): Long = sumOf { it.durationMs } / 1000

private fun List<PlayedTrack>.distinctArtists(): Long =
    mapTo(mutableSetOf()) { it.artist.lowercase() }.size.toLong()

/** How many days of listening `ALL` actually covers, so its per-day average means something. */
private fun spannedDays(plays: List<PlayedTrack>): Int {
    if (plays.isEmpty()) return 1
    val span = plays.maxOf { it.playedAt } - plays.minOf { it.playedAt }
    return ((span / TimeUnit.DAYS.toMillis(1)) + 1).toInt()
}

private fun Map<String, Int>.toEntries(): List<StatEntry> =
    entries
        .map { StatEntry(it.key, it.value.toLong()) }
        // Name as a tiebreak so equal counts do not reorder between refreshes.
        .sortedWith(compareByDescending<StatEntry> { it.value }.thenBy { it.name })
        .take(TOP_N)
