package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.core.database.entity.SharedPlaylistOpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedPlaylistDao {

    @Query("SELECT * FROM shared_playlists WHERE agroId = :agroId")
    suspend fun get(agroId: String): SharedPlaylistEntity?

    @Query("SELECT * FROM shared_playlists WHERE agroId = :agroId")
    fun observe(agroId: String): Flow<SharedPlaylistEntity?>

    @Query("SELECT * FROM shared_playlists WHERE localPlaylistId = :localPlaylistId")
    suspend fun forLocal(localPlaylistId: String): SharedPlaylistEntity?

    @Query("SELECT * FROM shared_playlists WHERE localPlaylistId = :localPlaylistId")
    fun observeForLocal(localPlaylistId: String): Flow<SharedPlaylistEntity?>

    /** The playlists this account follows rather than owns, newest first. */
    @Query("SELECT * FROM shared_playlists WHERE localPlaylistId IS NULL ORDER BY publishedAt DESC")
    suspend fun followed(): List<SharedPlaylistEntity>

    @Query("SELECT * FROM shared_playlists")
    suspend fun all(): List<SharedPlaylistEntity>

    /** Every copy, again whenever one is added, changed or forgotten: the library's cue to re-read. */
    @Query("SELECT * FROM shared_playlists")
    fun observeAll(): Flow<List<SharedPlaylistEntity>>

    @Upsert
    suspend fun upsert(playlist: SharedPlaylistEntity)

    @Query("UPDATE shared_playlists SET syncState = :state WHERE agroId = :agroId")
    suspend fun setState(agroId: String, state: String)

    @Query("UPDATE shared_playlists SET visibility = :visibility, editAccess = :editAccess WHERE agroId = :agroId")
    suspend fun setAccess(agroId: String, visibility: String, editAccess: String)

    @Query("SELECT * FROM shared_playlist_items WHERE agroId = :agroId ORDER BY position")
    suspend fun items(agroId: String): List<SharedPlaylistItemEntity>

    @Query("SELECT * FROM shared_playlist_items WHERE agroId = :agroId ORDER BY position")
    fun observeItems(agroId: String): Flow<List<SharedPlaylistItemEntity>>

    @Query("DELETE FROM shared_playlist_items WHERE agroId = :agroId")
    suspend fun clearItems(agroId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SharedPlaylistItemEntity>)

    /** Swaps every copy of [oldId] for [newId]: a placeholder matched to a real track. */
    @Query("UPDATE shared_playlist_items SET trackId = :newId WHERE trackId = :oldId")
    suspend fun replaceTrackId(oldId: String, newId: String): Int

    /** Replaces the whole track list in one transaction, so a reader never sees half of it. */
    @Transaction
    suspend fun replaceItems(agroId: String, items: List<SharedPlaylistItemEntity>) {
        clearItems(agroId)
        insertItems(items)
    }

    @Query("SELECT * FROM shared_playlist_ops WHERE agroId = :agroId ORDER BY seq")
    suspend fun ops(agroId: String): List<SharedPlaylistOpEntity>

    @Insert
    suspend fun insertOp(op: SharedPlaylistOpEntity)

    /** Forgets the edits up to and including [upTo]: the server has them now, or they were dropped. */
    @Query("DELETE FROM shared_playlist_ops WHERE agroId = :agroId AND seq <= :upTo")
    suspend fun clearOps(agroId: String, upTo: Long)

    @Query("DELETE FROM shared_playlist_ops WHERE seq = :seq")
    suspend fun deleteOp(seq: Long)

    /** Applies a local edit and records it to send, in one transaction. */
    @Transaction
    suspend fun applyLocalEdit(agroId: String, items: List<SharedPlaylistItemEntity>, ops: List<SharedPlaylistOpEntity>) {
        replaceItems(agroId, items)
        ops.forEach { insertOp(it) }
    }

    @Query("DELETE FROM shared_playlists WHERE agroId = :agroId")
    suspend fun deletePlaylist(agroId: String)

    @Query("DELETE FROM shared_playlist_ops WHERE agroId = :agroId")
    suspend fun deleteAllOps(agroId: String)

    /** Forgets a copy entirely: the playlist, its tracks and anything unsent. */
    @Transaction
    suspend fun forget(agroId: String) {
        deleteAllOps(agroId)
        clearItems(agroId)
        deletePlaylist(agroId)
    }
}
