package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wander.android.core.database.entity.ReplayRecapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplayRecapDao {

    /** Every saved recap, newest year first. */
    @Query("SELECT * FROM replay_recaps ORDER BY year DESC")
    fun observeAll(): Flow<List<ReplayRecapEntity>>

    @Query("SELECT * FROM replay_recaps WHERE year = :year")
    suspend fun get(year: Int): ReplayRecapEntity?

    @Query("SELECT year FROM replay_recaps ORDER BY year DESC")
    suspend fun savedYears(): List<Int>

    /**
     * Writes a recap, replacing the one for that year if there is one.
     *
     * Replacing rather than refusing: regenerating a year is how somebody sees the recap again
     * after opening the story a second time, and the newer one is computed from at least as much
     * history as the older.
     */
    @Upsert
    suspend fun save(recap: ReplayRecapEntity)

    /**
     * The lifetime figures, summed from the recaps rather than from `history`.
     *
     * This is what makes the tidy-up at the end of the story safe to accept: a year's contribution
     * to the totals survives the plays it was computed from. Null when nothing has been saved yet,
     * which `SUM` over no rows returns.
     */
    @Query(
        """
        SELECT SUM(totalMinutes) AS minutes, SUM(totalPlays) AS plays,
               MAX(longestStreakDays) AS bestStreak, COUNT(*) AS years
        FROM replay_recaps
        """
    )
    suspend fun lifetime(): ReplayLifetime?
}

/** Everything the saved recaps add up to. */
data class ReplayLifetime(
    val minutes: Long?,
    val plays: Long?,
    val bestStreak: Int?,
    val years: Int
)
