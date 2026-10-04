package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A playlist kept on the paired Agro server that this device holds a live copy of: one the user
 * published (then [localPlaylistId] is the Wanda playlist it was published from), or one someone
 * else shared that the user follows.
 *
 * [revision] is the server's version this copy was last brought up to; an edit is sent against it.
 * -1 means the copy has never been read back from the server — a publication from before copies
 * were kept in sync — and the first sync reconciles it.
 */
@Entity(tableName = "shared_playlists")
data class SharedPlaylistEntity(
    @PrimaryKey val agroId: String,
    val localPlaylistId: String? = null,
    val title: String,
    val description: String? = null,
    val ownerId: String,
    /** A `PlaylistVisibility` name. */
    val visibility: String,
    /** An `EditAccess` name. */
    val editAccess: String = "OFF",
    /** A `PlaylistRole` name: what this account may do with it. */
    val myRole: String,
    val revision: Long = UNSYNCED,
    /** A `SharedSyncState` name. */
    val syncState: String,
    val lastSyncedAt: Long = 0L,
    val publishedAt: Long = System.currentTimeMillis(),
    /** Written by Agro from its members' listening (see `AgroBlendApi`); never edited here. */
    val isBlend: Boolean = false
) {
    companion object {
        const val UNSYNCED = -1L
    }
}
