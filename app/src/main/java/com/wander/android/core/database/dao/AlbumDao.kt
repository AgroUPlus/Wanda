package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    /**
     * Library albums with no tracks stored for them, smallest first.
     *
     * The gap `MusicRepository.importMissingAlbumTracks` closes. A Subsonic album arrives from the
     * server's album list as metadata only — title, artist, sleeve, song count — and its tracks
     * are a separate request that used to be made only when somebody opened it. So the library
     * knew about the record and had none of the songs on it.
     *
     * Ordered by song count so the cheapest requests go first: a run is bounded, and finishing
     * many small albums makes more of the library recognisable than finishing one box set.
     */
    @Query(
        """
        SELECT a.* FROM albums a
        WHERE a.isLibrary = 1
          AND NOT EXISTS (SELECT 1 FROM tracks t WHERE t.albumId = a.id)
        ORDER BY a.songCount
        LIMIT :limit
        """
    )
    suspend fun libraryAlbumsWithoutTracks(limit: Int): List<AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbumsFlow(): Flow<List<AlbumEntity>>

    /** The same set, read once. For a resolver that answers a question and is done. */
    @Query("SELECT * FROM albums")
    suspend fun getAllAlbumsOnce(): List<AlbumEntity>

    /**
     * The user's own records, for the Library tab.
     *
     * [getAllAlbumsFlow] returns the whole table, which is also the cache backing artist pages and
     * album headers — so the Library tab was showing every record the app had ever drawn a tile
     * for. See [AlbumEntity.isLibrary].
     */
    @Query("SELECT * FROM albums WHERE isLibrary = 1 ORDER BY title ASC")
    fun getLibraryAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE source = :source ORDER BY title ASC")
    fun getAlbumsBySourceFlow(source: SourceType): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): AlbumEntity?

    /**
     * An artist's records, matched on the printed name rather than on `artistId`.
     *
     * Only Navidrome gives its tracks a stable artist id; YouTube Music rows carry none at all, so
     * an id-keyed discography would be empty for every streaming source. `COLLATE NOCASE` because
     * the same artist reaches Room capitalised differently from different backends.
     */
    @Query("SELECT * FROM albums WHERE artist = :artist COLLATE NOCASE ORDER BY year DESC, title ASC")
    fun getAlbumsByArtistFlow(artist: String): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    /**
     * Corrects who a known album is by, without touching anything else.
     *
     * A targeted update rather than a re-insert: an album already browsed carries a track count and
     * a duration that a tile off an artist shelf does not, and `REPLACE` would blank them. The
     * credit is the one field a shelf knows better, because the shelf is on that artist's own page.
     */
    @Query("UPDATE albums SET artist = :artist, artistId = :artistId WHERE id = :albumId")
    suspend fun updateAlbumArtist(albumId: String, artist: String, artistId: String?)

    @Query("DELETE FROM albums WHERE source = :source")
    suspend fun clearBySource(source: SourceType)
}
