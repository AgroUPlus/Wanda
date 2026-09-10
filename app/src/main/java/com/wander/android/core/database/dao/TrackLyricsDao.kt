package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.database.entity.TrackLyricsEntity
import kotlinx.coroutines.flow.Flow

import androidx.room.Embedded

/**
 * A search result combining track metadata, lyrics, and the highlighted search snippet.
 */
data class LyricSearchResult(
    @Embedded val track: TrackEntity,
    val plainLyrics: String,
    val syncedLyrics: String?,
    val snippet: String
)

@Dao
interface TrackLyricsDao {
    @Query("SELECT * FROM track_lyrics WHERE trackId = :trackId")
    suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity?

    @Query(
        """
        SELECT l.* FROM track_lyrics l
        LEFT JOIN tracks t ON t.id = l.trackId
        WHERE l.trackId = :trackId
           OR (LOWER(t.title) = LOWER(:title) AND LOWER(t.artist) = LOWER(:artist))
        ORDER BY CASE WHEN l.trackId = :trackId THEN 0 ELSE 1 END, l.syncedAt DESC
        LIMIT 1
        """
    )
    suspend fun findLyricsForTrackOrMetadata(trackId: String, title: String, artist: String): TrackLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: TrackLyricsEntity)

    @Query("INSERT INTO lyrics_fts(trackId, plainLyrics) VALUES (:trackId, :plainLyrics)")
    suspend fun insertFts(trackId: String, plainLyrics: String)

    @Query("DELETE FROM lyrics_fts WHERE trackId = :trackId")
    suspend fun deleteFts(trackId: String)

    @Query("DELETE FROM track_lyrics WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: String)

    @Transaction
    suspend fun saveLyricsWithFts(lyrics: TrackLyricsEntity) {
        insertLyrics(lyrics)
        deleteFts(lyrics.trackId)
        insertFts(lyrics.trackId, lyrics.plainLyrics)
    }

    @Transaction
    suspend fun deleteLyricsWithFts(trackId: String) {
        deleteLyrics(trackId)
        deleteFts(trackId)
    }

    @Transaction
    @Query(
        """
        SELECT t.*, l.plainLyrics, l.syncedLyrics, snippet(lyrics_fts, '<b>', '</b>', '...', -1, 10) AS snippet
        FROM lyrics_fts f
        JOIN track_lyrics l ON l.trackId = f.trackId
        JOIN tracks t ON t.id = l.trackId
        WHERE lyrics_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun searchTracksByLyrics(query: String, limit: Int = 25): List<LyricSearchResult>

    @Query("SELECT COUNT(*) FROM track_lyrics")
    suspend fun countLyrics(): Int

    /** How many lyrics this device got from the fleet rather than fetching for itself. */
    @Query("SELECT COUNT(*) FROM track_lyrics WHERE viaCatalog = 1")
    fun countFromCatalogueFlow(): Flow<Int>
}
