package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Wanda playlist that was published to the paired Agro server: its id there, and who it was
 * last set to be visible to. Kept so the visibility can be changed later without publishing again.
 *
 * Its own table rather than columns on `local_playlists`: most playlists are never published, and
 * the publication goes away with the server pairing, not with the playlist's tracks.
 */
@Entity(tableName = "playlist_publications")
data class PlaylistPublicationEntity(
    @PrimaryKey val playlistId: String,
    val agroId: String,
    /** A `PlaylistVisibility` name. */
    val visibility: String,
    val publishedAt: Long = System.currentTimeMillis()
)
