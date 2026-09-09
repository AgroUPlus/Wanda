package com.wander.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-Text Search virtual table over [TrackLyricsEntity.plainLyrics].
 *
 * Provides sub-millisecond BM25 ranking and snippet generation for lyrics queries.
 */
@Entity(tableName = "lyrics_fts")
@Fts4(tokenizer = "unicode61")
data class LyricsFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val trackId: String,
    val plainLyrics: String
)
