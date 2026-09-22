package com.wander.android.data.replay

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.ReplayRecapDao
import com.wander.android.core.database.entity.ReplayRecapEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroAccountApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves a recap, and only then offers to forget what it was computed from.
 *
 * The ordering *is* the feature. A recap is the last thing that remembers a year once its plays are
 * gone, so it is written and read back before anything is deleted — and if either step fails,
 * nothing is deleted at all.
 *
 * ## What survives
 *
 * The saved recap for every year, the lifetime totals summed from those recaps
 * ([ReplayRecapDao.lifetime]), each track's own `playCount` and `lastPlayedTimestamp` — which live
 * on `tracks` and are not derived from `history` — and every play from the recap year onward.
 *
 * ## What goes
 *
 * The individual play rows from *before* the recap year: the record of which song was played at
 * which minute. One consequence is worth saying out loud in the confirmation rather than
 * discovering next December: "artists you discovered" is computed against everything played
 * earlier, so forgetting those plays will make more artists look new next year.
 */
@Singleton
internal class ReplayArchivist @Inject constructor(
    private val recapDao: ReplayRecapDao,
    private val historyDao: HistoryDao,
    private val accountApi: AgroAccountApi,
    private val repository: ReplayRepository,
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Writes [report] down and reads it back.
     *
     * Reading it back is not paranoia about Room: it is what makes the delete that may follow
     * defensible. A recap that serialised to something this build cannot parse would otherwise be
     * discovered only once the plays were gone.
     */
    suspend fun save(report: ReplayReport): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            recapDao.save(
                ReplayRecapEntity(
                    year = report.year,
                    generatedAt = report.generatedAtMillis,
                    isFleetWide = report.isFleetWide,
                    totalMinutes = report.totalMinutes,
                    totalPlays = report.totalPlays,
                    longestStreakDays = report.longestStreakDays,
                    newArtistsCount = report.newArtistsCount,
                    payloadJson = json.encodeToString(report)
                )
            )

            val written = recapDao.get(report.year)
                ?: error("The recap for ${report.year} was not there after saving it")
            // Parsed, not merely present: a row that cannot be read back is not a saved recap.
            json.decodeFromString<ReplayReport>(written.payloadJson)
            Unit
        }
    }

    /** A saved recap, or null if that year has never been saved. */
    suspend fun saved(year: Int): ReplayReport? = withContext(Dispatchers.IO) {
        recapDao.get(year)?.let { row ->
            runCatching { json.decodeFromString<ReplayReport>(row.payloadJson) }.getOrNull()
        }
    }

    /** How many plays the tidy-up would forget, so the confirmation can name a real number. */
    suspend fun purgeableCount(year: Int, zone: ZoneId = ZoneId.systemDefault()): Int =
        withContext(Dispatchers.IO) {
            historyDao.countPurgeableBefore(repository.yearBounds(year, zone).first)
        }

    /**
     * Forgets the plays from before [year], server first.
     *
     * Server first, and locally only if the server agreed, so the two never disagree about what
     * still exists: a device that deleted its own rows and then failed to reach the server would
     * leave the account holding a history the user believes is gone, with nothing left on the phone
     * to retry from.
     *
     * Requires the recap to already be saved. It is the caller's job to have called [save] — this
     * checks anyway, because the cost of the check is nothing and the cost of being wrong is a year.
     */
    suspend fun purgeBefore(
        year: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (saved(year) == null) {
            return@withContext Result.failure(
                IllegalStateException("Refusing to forget ${year}'s plays: its recap is not saved")
            )
        }

        val cutoff = repository.yearBounds(year, zone).first

        if (secureStorage.agroConfigured.value) {
            val before = Instant.ofEpochMilli(cutoff).toString()
            accountApi.purgeScrobbles(before = before).getOrElse { cause ->
                // Reported rather than swallowed, and nothing is deleted here. The offer can be
                // accepted again once the server is reachable.
                return@withContext Result.failure(cause)
            }
        }

        runCatching { historyDao.deleteSyncedBefore(cutoff) }
    }
}
