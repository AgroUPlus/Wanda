package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics for a track, keeping both plain text and timestamped LRC formatting.
 *
 * Stored in its own table so `tracks` remains lean. Indexed by [LyricsFtsEntity] for
 * sub-millisecond full-text search across the entire library.
 */
@Entity(tableName = "track_lyrics")
data class TrackLyricsEntity(
    @PrimaryKey val trackId: String,
    val plainLyrics: String,
    val syncedLyrics: String? = null,
    val source: String,
    val syncedAt: Long = System.currentTimeMillis()
)
