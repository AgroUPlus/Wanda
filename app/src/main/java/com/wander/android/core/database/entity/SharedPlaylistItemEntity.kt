package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One track of a [SharedPlaylistEntity], as the server describes it, and the track on this device
 * it plays as: a matched track, or an `UNRESOLVED` placeholder until one is found.
 *
 * [itemId] is the server's id, except for a track this device added that the server has not
 * confirmed yet, which carries a `pending:` id until it has.
 */
@Entity(
    tableName = "shared_playlist_items",
    primaryKeys = ["agroId", "itemId"],
    indices = [Index("trackId")]
)
data class SharedPlaylistItemEntity(
    val agroId: String,
    val itemId: String,
    val position: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val addedBy: String? = null,
    val addedAt: String? = null
) {
    val isPending: Boolean get() = itemId.startsWith(PENDING_PREFIX)

    companion object {
        const val PENDING_PREFIX = "pending:"
    }
}
