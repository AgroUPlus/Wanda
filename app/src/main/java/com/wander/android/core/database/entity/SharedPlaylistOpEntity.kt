package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An edit made on this device to a shared playlist that the server has not confirmed yet, in the
 * order it was made. Kept in Room so an edit made offline, or interrupted by the app closing, is
 * still sent the next time this device syncs. [opJson] is a serialized `SharedPlaylistOp`.
 */
@Entity(tableName = "shared_playlist_ops", indices = [Index("agroId")])
data class SharedPlaylistOpEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val agroId: String,
    val opJson: String
)
