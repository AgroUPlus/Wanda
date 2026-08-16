package com.wander.android.data.repository

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.AgroStatsApi
import com.wander.android.data.sources.agro.StatEntry
import com.wander.android.data.sources.agro.StatsPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun stats(period: StatsPeriod): Result<AgroStats> = withContext(Dispatchers.IO) {
        if (isFleetWide) {
            // No local fallback on failure. Quietly substituting this device's smaller numbers for
            // the fleet's would look like listening had disappeared, with nothing on screen to say
            // the source had changed.
            statsApi.listeningStats(period)
        } else {
            runCatching { localStats(period) }
        }
    }

    /**
     * What Room can say alone.
     *
     * Room keeps play *counts* per track and a history of when plays happened, but not how long
     * each one lasted — so seconds are reconstructed as "the track's duration, once per play",
     * which is what the play was worth if it ran to the end. It is an estimate, and the screen
     * labels it as this device only.
     */
    private suspend fun localStats(period: StatsPeriod): AgroStats {
        val now = System.currentTimeMillis()
        val since = period.startMillis(now)
        val history = historyDao.getHistorySince(since)

        val dayAgo = now - TimeUnit.DAYS.toMillis(1)
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)

        val byDay = LongArray(DAY_BUCKETS)
        val byHour = LongArray(24)
        var secondsToday = 0L
        var secondsWeek = 0L
        var secondsTotal = 0L
        val playedDays = mutableSetOf<Int>()

        history.forEach { play ->
            val seconds = play.durationMs / 1000
            secondsTotal += seconds
            if (play.playedAt >= dayAgo) secondsToday += seconds
            if (play.playedAt >= weekAgo) secondsWeek += seconds

            val daysAgo = ((now - play.playedAt) / TimeUnit.DAYS.toMillis(1)).toInt()
            if (daysAgo in 0 until DAY_BUCKETS) byDay[DAY_BUCKETS - 1 - daysAgo] += seconds
            if (daysAgo >= 0) playedDays += daysAgo
            byHour[((play.playedAt / 3_600_000) % 24).toInt()] += seconds
        }

        var streak = 0
        while (streak in playedDays) streak++

        val topTracks = trackDao.getTopPlayedTracks(TOP_N).map { track ->
            StatEntry("${track.title} — ${track.artist}", track.playCount.toLong())
        }

        return AgroStats(
            secondsToday = secondsToday,
            secondsWeek = secondsWeek,
            secondsTotal = secondsTotal,
            playCount = history.size.toLong(),
            streakDays = streak,
            topArtists = history.groupingBy { it.artist }.eachCount().toEntries(),
            topAlbums = history
                .groupingBy { "${it.album ?: "Unknown Album"} — ${it.artist}" }
                .eachCount()
                .toEntries(),
            topTracks = topTracks,
            byDay = byDay.toList(),
            byHour = byHour.toList(),
            // Nothing to break down: without Agro this device is the only one there is.
            byDevice = emptyList()
        )
    }
}

private const val DAY_BUCKETS = 14
private const val TOP_N = 10

private fun Map<String, Int>.toEntries(): List<StatEntry> =
    entries
        .map { StatEntry(it.key, it.value.toLong()) }
        // Name as a tiebreak so equal counts do not reorder between refreshes.
        .sortedWith(compareByDescending<StatEntry> { it.value }.thenBy { it.name })
        .take(TOP_N)

private fun StatsPeriod.startMillis(now: Long): Long = when (this) {
    StatsPeriod.WEEK -> now - TimeUnit.DAYS.toMillis(7)
    StatsPeriod.MONTH -> now - TimeUnit.DAYS.toMillis(30)
    StatsPeriod.YEAR -> now - TimeUnit.DAYS.toMillis(365)
    StatsPeriod.ALL -> Long.MIN_VALUE
}
