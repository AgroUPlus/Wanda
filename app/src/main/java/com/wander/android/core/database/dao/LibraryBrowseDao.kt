package com.wander.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * The order of a Library track list, chosen at run time by `:sort` (a `LibrarySort` name).
 *
 * `CASE` arms rather than one query per order: Room cannot bind a column name, and seven orders
 * times four lists would be twenty-eight queries saying the same thing. An arm whose order is not
 * the chosen one is `NULL` for every row, so it never decides anything. Names compare `NOCASE` so
 * "a" and "A" sit together under one letter of the fast scroller; the title and id at the end make
 * every order total, which paging needs to keep its offsets stable.
 */
internal const val TRACK_ORDER = """
    CASE WHEN :sort = 'ARTIST' THEN artist END COLLATE NOCASE ASC,
    CASE WHEN :sort = 'ALBUM' THEN album END COLLATE NOCASE ASC,
    CASE WHEN :sort = 'ALBUM' THEN discNumber END ASC,
    CASE WHEN :sort = 'ALBUM' THEN trackNumber END ASC,
    CASE WHEN :sort = 'DATE_ADDED' THEN addedTimestamp END DESC,
    CASE WHEN :sort = 'YEAR' THEN year END DESC,
    CASE WHEN :sort = 'MOST_PLAYED' THEN playCount END DESC,
    CASE WHEN :sort = 'RECENTLY_PLAYED' THEN lastPlayedTimestamp END DESC,
    title COLLATE NOCASE ASC, id ASC
"""

/** The Tracks tab's rows: the user's own songs, no episodes. */
internal const val LIBRARY_TRACKS = "isLibrary = 1 AND isEpisode = 0 AND source IN (:sources)"

/**
 * Read queries behind the Library's lists, each in the order and for the sources the user chose.
 *
 * `sources` is never empty: "every source" is passed as every source, so one `IN` serves both the
 * filtered and the unfiltered list.
 */
@Dao
interface LibraryBrowseDao {

    @Query("SELECT * FROM tracks WHERE $LIBRARY_TRACKS ORDER BY $TRACK_ORDER")
    fun pagedLibraryTracks(sort: String, sources: List<SourceType>): PagingSource<Int, TrackEntity>

    /** The ids of [pagedLibraryTracks] in the same order: what a tap on one row queues around. */
    @Query("SELECT id FROM tracks WHERE $LIBRARY_TRACKS ORDER BY $TRACK_ORDER")
    suspend fun libraryTrackIds(sort: String, sources: List<SourceType>): List<String>

    @Query(
        "SELECT * FROM tracks WHERE isLiked = 1 AND isEpisode = 0 AND source IN (:sources) ORDER BY $TRACK_ORDER"
    )
    fun observeLikedTracks(sort: String, sources: List<SourceType>): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE isDownloaded = 1 AND isEpisode = 0 AND source != 'LOCAL' AND source IN (:sources)
        ORDER BY $TRACK_ORDER
        """
    )
    fun observeDownloadedTracks(sort: String, sources: List<SourceType>): Flow<List<TrackEntity>>

    /**
     * Library albums ordered by what the album row itself knows: title, artist or year.
     *
     * Kept apart from [observeLibraryAlbumsByActivity] so these orders observe `albums` only — the
     * other one reads `tracks` and so re-runs on every play.
     */
    @Query(
        """
        SELECT * FROM albums
        WHERE isLibrary = 1 AND source IN (:sources)
        ORDER BY
            CASE WHEN :sort = 'ARTIST' THEN artist END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'YEAR' THEN year END DESC,
            title COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeLibraryAlbums(sort: String, sources: List<SourceType>): Flow<List<AlbumEntity>>

    /**
     * Library albums ordered by their tracks: newest addition, total plays, or latest play.
     *
     * An album row carries none of that, so each arm asks its tracks. Correlated subqueries rather
     * than a join and `GROUP BY`: `CASE` only evaluates the arm that matches, so an order reads
     * `tracks` once per album for its own column and never for the other two.
     */
    @Query(
        """
        SELECT a.* FROM albums a
        WHERE a.isLibrary = 1 AND a.source IN (:sources)
        ORDER BY
            CASE WHEN :sort = 'DATE_ADDED'
                THEN (SELECT MAX(t.addedTimestamp) FROM tracks t WHERE t.albumId = a.id) END DESC,
            CASE WHEN :sort = 'MOST_PLAYED'
                THEN (SELECT SUM(t.playCount) FROM tracks t WHERE t.albumId = a.id) END DESC,
            CASE WHEN :sort = 'RECENTLY_PLAYED'
                THEN (SELECT MAX(t.lastPlayedTimestamp) FROM tracks t WHERE t.albumId = a.id) END DESC,
            a.title COLLATE NOCASE ASC, a.id ASC
        """
    )
    fun observeLibraryAlbumsByActivity(sort: String, sources: List<SourceType>): Flow<List<AlbumEntity>>
}
