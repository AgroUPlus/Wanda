package com.wander.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/** Full-text index over [CatalogRecordingEntity.plainLyrics], the way `lyrics_fts` is over a track's. */
@Entity(tableName = "catalog_lyrics_fts")
@Fts4(tokenizer = "unicode61")
data class CatalogLyricsFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val recordingId: String,
    val plainLyrics: String
)
