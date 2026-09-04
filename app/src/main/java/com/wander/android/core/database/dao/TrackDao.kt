package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.paging.PagingSource
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.core.database.entity.TrackSourceFields
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

/** SQLite returns this rowId from an INSERT OR IGNORE that hit an existing row. */
private const val CONFLICT_ROW_ID = -1L

@Dao
interface TrackDao {

    /**
     * The Library screen. Restricted to `isLibrary` rows so that searching, radio and Archive
     * browsing — all of which persist their results for offline use — do not grow the library.
     */
    @Query("SELECT * FROM tracks WHERE isLibrary = 1 ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    /**
     * The same rows, a page at a time.
     *
     * Room reads only the window the list is showing and re-reads only the pages that changed, so a
     * play count ticking over no longer costs a full re-read and a full re-map of the library. The
     * flow above did exactly that on every single write to `tracks` — likes, play counts, a sync —
     * and at a thousand rows it is what made scrolling stutter.
     */
    @Query("SELECT * FROM tracks WHERE isLibrary = 1 ORDER BY title ASC")
    fun pagedTracks(): PagingSource<Int, TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND source = :source ORDER BY title ASC")
    fun pagedTracksBySource(source: SourceType): PagingSource<Int, TrackEntity>

    /**
     * Every library track's id, in the order the list shows them.
     *
     * What a tap needs and a page cannot give: playing a track means queueing the library around
     * it, and with paging the screen no longer holds the whole list to hand over. Ids only, because
     * the queue is built from ids and loading a thousand full rows to find a position would put
     * back the cost paging just removed.
     */
    @Query("SELECT id FROM tracks WHERE isLibrary = 1 ORDER BY title ASC")
    suspend fun libraryTrackIds(): List<String>

    @Query("SELECT id FROM tracks WHERE isLibrary = 1 AND source = :source ORDER BY title ASC")
    suspend fun libraryTrackIdsBySource(source: SourceType): List<String>

    /**
     * Every row, library or not, once.
     *
     * Unfiltered on purpose: the recording migration has to account for *all* renditions, including
     * the non-library rows a search or an artist page left behind — those carry likes and play
     * counts too, and a migration that skipped them would strand them.
     */
    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLibrary = 1 AND source = :source ORDER BY title ASC")
    fun getTracksBySourceFlow(source: SourceType): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getLikedTracksFlow(): Flow<List<TrackEntity>>

    /**
     * Just the ids, for screens that hold their own track list (search results, the queue) and
     * only need to know which rows are liked. Emitting whole entities there would replace the
     * list the user is looking at every time an unrelated like changed.
     */
    @Query("SELECT id FROM tracks WHERE isLiked = 1")
    fun getLikedTrackIdsFlow(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 AND source != 'LOCAL' ORDER BY title ASC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>

    /** Everything playable with no network: local files, and anything the downloader has written. */
    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 OR source = 'LOCAL'")
    suspend fun getOfflineTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getTracksByAlbumFlow(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    /** Candidate track ids within duration tolerance for duplicate matching, excluding target track. */
    @Query("SELECT id FROM tracks WHERE id != :excludingId AND durationMs BETWEEN :minDurationMs AND :maxDurationMs")
    suspend fun getCandidateIdsByDuration(excludingId: String, minDurationMs: Long, maxDurationMs: Long): List<String>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    suspend fun getTracksInAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title ASC")
    suspend fun getTracksInSource(source: SourceType): List<TrackEntity>

    /**
     * Everything by an artist, most played first, matched on the printed name — see
     * [com.wander.android.core.database.dao.AlbumDao.getAlbumsByArtistFlow] for why not `artistId`.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE artist = :artist COLLATE NOCASE
        ORDER BY playCount DESC, title ASC
        """
    )
    fun getTracksByArtistFlow(artist: String): Flow<List<TrackEntity>>

    /**
     * Everything under one artist name, once.
     *
     * The candidate set for finding a track's other renditions. Name-matched and case-folded on
     * purpose — it is a net, not an answer; `TrackDeduplicator.isSameRecording` decides which of
     * the catch is actually the same performance.
     */
    @Query("SELECT * FROM tracks WHERE artist = :artist COLLATE NOCASE")
    suspend fun getTracksByArtistOnce(artist: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1")
    suspend fun getLikedTracksOnce(): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchTracks(query: String, limit: Int = 50): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE source = :source
          AND (title LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR album LIKE '%' || :query || '%')
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchTracksInSource(source: SourceType, query: String, limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTracksInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedInSource(source: SourceType, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyAddedTracks(limit: Int = 30): List<TrackEntity>

    /**
     * Records that a track turned out to be a livestream.
     *
     * Written when a stream actually resolves to a manifest, which is the only moment anything
     * knows for certain — the badges a search row carries are a guess, and one that YouTube
     * changes the shape of. Stored so the *next* play sets the container hint before loading
     * rather than discovering it from a parse failure.
     */
    @Query("UPDATE tracks SET isLive = 1 WHERE id = :trackId AND isLive = 0")
    suspend fun markLive(trackId: String)

    /**
     * The album ids most recently added to, newest first.
     *
     * Grouped rather than distinct-on-a-track-list, so an album whose tracks arrived together
     * counts once and is ordered by its newest track. Albums have no timestamp of their own —
     * they are derived from the tracks filed under them — so this is where "recently added"
     * actually lives.
     */
    @Query(
        """
        SELECT albumId FROM tracks
        WHERE albumId IS NOT NULL
        GROUP BY albumId
        ORDER BY MAX(addedTimestamp) DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyAddedAlbumIds(limit: Int = 12): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp IS NOT NULL ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY lastPlayedTimestamp DESC, addedTimestamp DESC LIMIT :limit")
    suspend fun getLikedTracksList(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayedTracks(limit: Int = 30): List<TrackEntity>

    /**
     * Every row that has ever been played, so plays can be totalled per *recording*.
     *
     * Unlimited on purpose. A recording's total is the sum over its copies, and a copy played
     * twice can sit far down a list ordered by row — taking the top N rows first and grouping
     * afterwards would drop exactly the small contributions that make a total large.
     */
    @Query("SELECT * FROM tracks WHERE playCount > 0")
    suspend fun getPlayedTracksOnce(): List<TrackEntity>

    /** Well-loved tracks the user has not returned to lately — the "forgotten favourites" mix. */
    @Query(
        """
        SELECT * FROM tracks
        WHERE playCount > 3
          AND (lastPlayedTimestamp IS NULL OR lastPlayedTimestamp < :thresholdTimestamp)
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    suspend fun getForgottenFavorites(thresholdTimestamp: Long, limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE playCount = 0 ORDER BY addedTimestamp DESC LIMIT :limit")
    suspend fun getNeverPlayedTracks(limit: Int = 30): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND isDownloaded = 0 AND source != 'LOCAL' LIMIT :limit")
    suspend fun getLikedNotDownloaded(limit: Int): List<TrackEntity>

    /**
     * Insert-or-refresh that cannot destroy user state.
     *
     * A plain `@Insert(REPLACE)` is a DELETE followed by an INSERT, so refetching a track the user
     * had already downloaded wiped its `localFilePath`, `isLiked` and `playCount`. Instead: insert
     * only rows we do not have, then update the remaining ones through [TrackSourceFields], which
     * carries backend metadata and nothing else.
     */
    @Transaction
    suspend fun upsertTracks(tracks: List<TrackEntity>) {
        // A one-shot id names a transfer, not a track — see [isOneShotTrackId]. Storing one wrote
        // a row with `source = LOCAL`, no file, and a dead relay URL in `streamUri`, which the
        // offline-first tier then matched by title ahead of the real file. Every later attempt to
        // play that song resolved to the same expired session and answered 401, permanently.
        val storable = tracks.filterNot { isOneShotTrackId(it.id) }
        if (storable.isEmpty()) return
        val rowIds = insertNewTracks(storable)
        val existing = storable.filterIndexed { index, _ -> rowIds[index] == CONFLICT_ROW_ID }
        if (existing.isNotEmpty()) {
            updateSourceFields(existing.map { it.toSourceFields() })
        }
    }

    /**
     * Deletes rows that should never have been written — see [upsertTracks].
     *
     * Kept as a query rather than a migration because it is also a repair: a build that wrote one
     * of these may run again before any migration would, and the row has to go the moment it is
     * noticed.
     */
    @Query("DELETE FROM tracks WHERE id LIKE 'relay:%' OR id LIKE 'p2p:%'")
    suspend fun deleteOneShotTrackRows(): Int

    /** Returns -1 for every row that already existed, leaving it untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewTracks(tracks: List<TrackEntity>): List<Long>

    @Update(entity = TrackEntity::class)
    suspend fun updateSourceFields(fields: List<TrackSourceFields>)

    /**
     * Promotes tracks into the library. Deliberately one-way: a track that is part of the user's
     * collection must not fall out of it just because a later search happened to return it.
     */
    @Query("UPDATE tracks SET isLibrary = 1 WHERE id IN (:trackIds)")
    suspend fun markAsLibrary(trackIds: List<String>)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, isLiked: Boolean)

    /**
     * Writes corrected display metadata onto a row.
     *
     * Separate from [updateSourceFields] and deliberately not part of it: that method carries what
     * a backend last said, and is overwritten wholesale by the next refetch. This carries what the
     * shared catalogue worked out, which is re-applied from `canonical_metadata` after each sync
     * precisely because the refetch will have undone it.
     */
    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album WHERE id = :trackId")
    suspend fun setDisplayMetadata(trackId: String, title: String, artist: String, album: String?)

    /**
     * Everything that could be fingerprinted: the whole library.
     *
     * This used to be `localFilePath IS NOT NULL`, on the reasoning that a track living only on a
     * server has no bytes here to read. That was true of the query and false of the device: the
     * bytes are one ranged request away, and `PcmDecoder` only ever wanted the first minute of
     * them. The old rule quietly meant that on a library made mostly of Navidrome and YouTube
     * Music, the recogniser and the radio knew about a handful of songs.
     *
     * Livestreams are excluded here rather than downstream: there is no fixed audio to identify,
     * and the worker would decode a different minute every run.
     */
    @Query("SELECT * FROM tracks WHERE isLive = 0")
    suspend fun getFingerprintableTracks(): List<TrackEntity>

    /**
     * The same set as a flow, for the Fingerprints screen.
     *
     * That screen used `getAllTracksFlow`, which is `isLibrary = 1` — the Library screen's rule.
     * So it reported on a fraction of what the indexer actually measures: a library of 1,368
     * fingerprintable tracks showed as 200, with every YouTube Music track missing entirely, and
     * the question the screen exists to answer ("why can't Wanda hear this song?") could not be
     * asked about the songs most likely to be missing.
     */
    @Query("SELECT * FROM tracks WHERE isLive = 0")
    fun getFingerprintableTracksFlow(): Flow<List<TrackEntity>>

    /**
     * Every row on this device sharing a title, for the caller to judge.
     *
     * Deliberately *candidates*, not an answer. This used to be a single `LIMIT 1` row, which made
     * a title the only thing standing between two recordings: every track called "Memories" played
     * whichever one happened to be first, because the artist was never looked at. Picking the row
     * is [com.wander.android.data.repository.TrackDeduplicator]'s job, so the query hands back the
     * whole bucket and stays out of it.
     *
     * Ordered so a downloaded copy is preferred over a local-only one, which is the one preference
     * the caller cannot reconstruct from the tags.
     */
    @Query(
        """
        SELECT * FROM tracks 
        WHERE ((localFilePath IS NOT NULL AND localFilePath != '') OR source = 'LOCAL')
          AND title = :title COLLATE NOCASE 
        ORDER BY CASE WHEN (localFilePath IS NOT NULL AND localFilePath != '') THEN 0 ELSE 1 END
        LIMIT :limit
        """
    )
    suspend fun findLocalOrDownloadedCandidates(title: String, limit: Int): List<TrackEntity>

    /** The Navidrome rows sharing a title. Same contract as [findLocalOrDownloadedCandidates]. */
    @Query(
        """
        SELECT * FROM tracks 
        WHERE source = 'NAVIDROME' 
          AND title = :title COLLATE NOCASE 
        LIMIT :limit
        """
    )
    suspend fun findNavidromeCandidates(title: String, limit: Int): List<TrackEntity>

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, localFilePath = :localPath WHERE id = :trackId")
    suspend fun setDownloaded(trackId: String, isDownloaded: Boolean, localPath: String?)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long)

    // ── Library sync ────────────────────────────────────────────────────────────────────────

    /**
     * On-device tracks whose bytes have not been hashed yet.
     *
     * Only `LOCAL`: a Navidrome or YouTube Music track is not a file this device could upload.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE source = 'LOCAL' AND contentHash IS NULL AND streamUri IS NOT NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnhashedLocalTracks(limit: Int): List<TrackEntity>

    /**
     * Downloaded files that have never been hashed.
     *
     * The hashing pass only ever looked at `source = 'LOCAL'`, so a track downloaded from
     * Navidrome or YouTube Music kept a null `contentHash` no matter how long it sat on the disk.
     * That is the whole reason off-grid listen-along could not address them: the peer tier asks
     * for audio by content hash, and 13 of the 24 files actually held on one real device had none.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE source != 'LOCAL'
          AND (contentHash IS NULL OR contentHash = '')
          AND isDownloaded = 1
          AND localFilePath IS NOT NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnhashedDownloads(limit: Int): List<TrackEntity>

    /**
     * Records a duration read from the audio itself.
     *
     * Only ever fills a gap — `durationMs > 0` is left alone — because the source's own answer is
     * the better one where it exists, and this is measured from whatever the decoder could reach.
     */
    @Query("UPDATE tracks SET durationMs = :durationMs WHERE id = :trackId AND (durationMs IS NULL OR durationMs <= 0)")
    suspend fun fillMissingDuration(trackId: String, durationMs: Long)

    @Query("UPDATE tracks SET contentHash = :hash WHERE id = :trackId")
    suspend fun setContentHash(trackId: String, hash: String)

    /** Hashed local tracks the server has not confirmed it holds. These are what get uploaded. */
    @Query(
        """
        SELECT * FROM tracks
        WHERE source = 'LOCAL' AND contentHash IS NOT NULL AND syncedAt IS NULL
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getUnsyncedLocalTracks(limit: Int): List<TrackEntity>

    @Query("UPDATE tracks SET syncedAt = :timestamp WHERE id = :trackId")
    suspend fun markSynced(trackId: String, timestamp: Long)

    /**
     * Local tracks the server has confirmed, which are therefore safe to offer to delete: the
     * bytes exist somewhere other than this phone.
     */
    @Query(
        "SELECT * FROM tracks WHERE source = 'LOCAL' AND syncedAt IS NOT NULL ORDER BY artist, album, trackNumber"
    )
    suspend fun getSyncedLocalTracks(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL' AND contentHash IS NOT NULL AND syncedAt IS NULL")
    fun countPendingUploadFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL' AND syncedAt IS NOT NULL")
    fun countSyncedFlow(): Flow<Int>

    /** Local audio on this device at all — the denominator the sync counters are read against. */
    @Query("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL'")
    fun countLocalFlow(): Flow<Int>

    /**
     * Drops local rows whose file is gone.
     *
     * Passed the ids that still exist rather than the ones that do not: MediaStore is the
     * authority on what is on the device, and asking it "what is there" is one cheap query while
     * asking "is this one still there" is one query per row.
     */
    /** The hashes about to be pruned, read before the delete so they can be reported as gone. */
    @Query(
        """
        SELECT contentHash FROM tracks
        WHERE source = 'LOCAL' AND id NOT IN (:keepIds) AND contentHash IS NOT NULL
        """
    )
    suspend fun localContentHashesNotIn(keepIds: List<String>): List<String>

    @Query("DELETE FROM tracks WHERE source = 'LOCAL' AND id NOT IN (:keepIds)")
    suspend fun deleteLocalTracksNotIn(keepIds: List<String>): Int

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun clearBySource(source: SourceType)

    /** Bulk lookup for rebuilding a cached shelf, in one query rather than one per track. */
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>

    /** Resolves a local track entity by its content SHA-256 hash. */
    @Query("SELECT * FROM tracks WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): TrackEntity?
}
