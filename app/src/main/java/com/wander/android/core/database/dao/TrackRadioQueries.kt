package com.wander.android.core.database.dao

import androidx.room.Query
import com.wander.android.core.database.entity.TrackEntity

/**
 * Read queries that pull radio candidates out of the user's own collection.
 *
 * Apart from [TrackLibraryQueries], which is at the file-size cap.
 */
interface TrackRadioQueries {

    /**
     * The user's own songs that share a seed's primary [artist] or [genre], for a radio around it.
     *
     * Collection-only on purpose: a radio pool drawn from every cached row would hand back the
     * results of whatever was searched last. [artist] blank or [genre] null each turn their half
     * of the match off rather than matching everything.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE id != :seedId AND isEpisode = 0 AND isLive = 0 AND source != 'UNRESOLVED'
          AND $IN_COLLECTION
          AND ((:artist != '' AND artist LIKE '%' || :artist || '%' COLLATE NOCASE)
            OR (:genre IS NOT NULL AND LOWER(genre) = LOWER(:genre)))
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getSeedNeighbours(seedId: String, artist: String, genre: String?, limit: Int): List<TrackEntity>
}
