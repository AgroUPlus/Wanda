package com.wander.android.core.cache.predict

import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.isOneShotTrackId
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow

/** One remembered play: which track, and when. */
data class PastPlay(val trackId: String, val playedAt: Long)

/**
 * Guesses which tracks are worth having on the device before the next time it is offline.
 *
 * Pure and deterministic — the same history, library and clock give the same answer — so the
 * download worker only gathers inputs and this decides.
 *
 * ## The score
 *
 * Each play of a track adds a weight that halves every [HALF_LIFE_DAYS]: a song played daily this
 * week outranks one played a hundred times last year. That weight is raised by how well the play
 * fits the coming day:
 *
 * - **Time of day.** Plays at the hours this listener plays most (their commute, their gym hour)
 *   count up to [HOUR_WEIGHT] more — those are the hours a phone is most often away from Wi-Fi.
 * - **Day of week.** Plays on the weekday of today or tomorrow count [WEEKDAY_WEIGHT] more: the
 *   download runs overnight, and Monday's music is not Saturday's.
 *
 * A library track added recently gets a small bonus, worth at most half a fresh play, so new
 * arrivals fill whatever room listening leaves. When most of the library looks recently added it
 * is a first sync rather than a choice, and the bonus is not given.
 *
 * Skips would be a strong negative signal, but history does not record them, so they are not
 * guessed at.
 *
 * Tracks already on the device (downloaded or local files), borrowed one-shot tracks, livestreams,
 * podcast episodes and unresolved imports are never proposed.
 */
class OfflinePredictor(private val zone: ZoneId) {

    /** Up to [limit] track ids, best first. A track with no evidence at all is not proposed. */
    fun rank(tracks: List<TrackEntity>, plays: List<PastPlay>, now: Long, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val eligible = tracks.filter { it.isPredictable() }.associateBy { it.id }
        if (eligible.isEmpty()) return emptyList()

        val recent = plays.filter { it.playedAt <= now && now - it.playedAt <= WINDOW_MS }
        val hourShare = hourShares(recent, now)
        val today = dayOf(now)
        val coming = setOf(today, today.plus(1))

        val scores = HashMap<String, Double>()
        for (play in recent) {
            if (play.trackId !in eligible) continue
            val recency = decay(now - play.playedAt, HALF_LIFE_DAYS)
            val hourFit = 1.0 + HOUR_WEIGHT * (hourShare[hourOf(play.playedAt)] ?: 0.0)
            val dayFit = if (dayOf(play.playedAt) in coming) 1.0 + WEEKDAY_WEIGHT else 1.0
            scores.merge(play.trackId, recency * hourFit * dayFit, Double::plus)
        }

        if (!looksLikeFirstSync(eligible.values, now)) {
            eligible.values.filter { it.isLibrary && now - it.addedTimestamp in 0..RECENTLY_ADDED_MS }.forEach {
                scores.merge(it.id, RECENTLY_ADDED_BONUS * decay(now - it.addedTimestamp, ADDED_HALF_LIFE_DAYS), Double::plus)
            }
        }

        return scores.entries
            .filter { it.value > 0.0 }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    /** Each hour's recency-weighted share of listening, scaled so the busiest hour is 1. */
    private fun hourShares(plays: List<PastPlay>, now: Long): Map<Int, Double> {
        val totals = HashMap<Int, Double>()
        plays.forEach { totals.merge(hourOf(it.playedAt), decay(now - it.playedAt, HALF_LIFE_DAYS), Double::plus) }
        val peak = totals.values.maxOrNull() ?: return emptyMap()
        return totals.mapValues { it.value / peak }
    }

    private fun looksLikeFirstSync(tracks: Collection<TrackEntity>, now: Long): Boolean {
        val library = tracks.filter { it.isLibrary }
        if (library.size < FIRST_SYNC_MIN_LIBRARY) return false
        val fresh = library.count { now - it.addedTimestamp in 0..RECENTLY_ADDED_MS }
        return fresh > library.size * FIRST_SYNC_SHARE
    }

    private fun TrackEntity.isPredictable(): Boolean =
        !isDownloaded && localFilePath == null && !isLive && !isEpisode && !isOneShotTrackId(id) &&
            effectiveSource != SourceType.LOCAL && effectiveSource != SourceType.UNRESOLVED

    private fun hourOf(at: Long): Int = Instant.ofEpochMilli(at).atZone(zone).hour
    private fun dayOf(at: Long): DayOfWeek = Instant.ofEpochMilli(at).atZone(zone).dayOfWeek

    private fun decay(ageMs: Long, halfLifeDays: Double): Double =
        0.5.pow(ageMs.coerceAtLeast(0L) / (halfLifeDays * DAY_MS))

    companion object {
        private const val DAY_MS = 86_400_000.0

        /** How far back history is read; older plays have decayed below a rounding error anyway. */
        const val WINDOW_MS = 120L * 86_400_000L
        const val HALF_LIFE_DAYS = 14.0
        const val HOUR_WEIGHT = 0.5
        const val WEEKDAY_WEIGHT = 0.25
        const val RECENTLY_ADDED_BONUS = 0.5
        const val ADDED_HALF_LIFE_DAYS = 7.0
        const val RECENTLY_ADDED_MS = 30L * 86_400_000L
        private const val FIRST_SYNC_MIN_LIBRARY = 20
        private const val FIRST_SYNC_SHARE = 0.5
    }
}
