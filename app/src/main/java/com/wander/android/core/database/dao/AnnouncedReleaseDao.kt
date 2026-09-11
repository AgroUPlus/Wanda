package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.AnnouncedReleaseEntity

/** Which releases have already been announced. See [AnnouncedReleaseEntity] for why this exists. */
@Dao
interface AnnouncedReleaseDao {
    /**
     * The subset of [ids] already announced.
     *
     * Asked about the batch in hand rather than read whole: the table only grows, and a caller that
     * loaded every id it had ever seen to filter a dozen would get slower every release.
     */
    @Query("SELECT recordingId FROM announced_releases WHERE recordingId IN (:ids)")
    suspend fun announcedAmong(ids: List<String>): List<String>

    /**
     * Records that these have been announced.
     *
     * `IGNORE` rather than `REPLACE`: the interesting figure is when a release was *first* said out
     * loud, and a republish arriving twice must not rewrite that to today.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markAnnounced(rows: List<AnnouncedReleaseEntity>)
}
