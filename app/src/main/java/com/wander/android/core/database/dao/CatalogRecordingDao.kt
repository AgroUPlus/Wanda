package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.wander.android.core.database.entity.CatalogRecordingEntity

/** A catalogue recording whose lyrics matched a search, with the highlighted snippet. */
data class CatalogLyricHit(
    @Embedded val recording: CatalogRecordingEntity,
    val snippet: String
)

/** A catalogue recording's segment vectors, without the rest of the row. */
class CatalogVector(val recordingId: String, val vector: ByteArray)

@Dao
interface CatalogRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: CatalogRecordingEntity)

    @Query("INSERT INTO catalog_lyrics_fts(recordingId, plainLyrics) VALUES (:recordingId, :plainLyrics)")
    suspend fun insertFts(recordingId: String, plainLyrics: String)

    @Query("DELETE FROM catalog_lyrics_fts WHERE recordingId = :recordingId")
    suspend fun deleteFts(recordingId: String)

    /** Replaces the recording and its place in the lyrics index together. */
    @Transaction
    suspend fun save(recording: CatalogRecordingEntity) {
        insert(recording)
        deleteFts(recording.recordingId)
        recording.plainLyrics?.takeIf { it.isNotBlank() }?.let { insertFts(recording.recordingId, it) }
    }

    @Query("SELECT * FROM catalog_recordings WHERE recordingId = :recordingId")
    suspend fun byId(recordingId: String): CatalogRecordingEntity?

    /** Ids carry [PREFIX] so they can sit beside track ids in one shortlist. */
    @Query("SELECT '$PREFIX' || recordingId AS trackId, centroid FROM catalog_recordings WHERE model = :model AND version = :version")
    suspend fun centroids(model: String, version: Int): List<Centroid>

    @Query("SELECT COUNT(*) FROM catalog_recordings WHERE model = :model AND version = :version")
    fun countFlow(model: String, version: Int): Flow<Int>

    @Query("SELECT recordingId, vector FROM catalog_recordings WHERE recordingId IN (:ids) AND model = :model AND version = :version")
    suspend fun vectors(ids: List<String>, model: String, version: Int): List<CatalogVector>

    @Query(
        """
        SELECT c.*, snippet(catalog_lyrics_fts, '<b>', '</b>', '...', -1, 10) AS snippet
        FROM catalog_lyrics_fts f
        JOIN catalog_recordings c ON c.recordingId = f.recordingId
        WHERE catalog_lyrics_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun searchByLyrics(query: String, limit: Int): List<CatalogLyricHit>

    companion object {
        /** What marks a shortlist id as a catalogue recording rather than a library track. */
        const val PREFIX = "catalog:"
    }
}
