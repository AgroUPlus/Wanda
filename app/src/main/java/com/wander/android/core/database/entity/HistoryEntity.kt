package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per play. Doubles as the scrobble outbox: a play recorded while offline keeps
 * `scrobbled = false` until the source accepts it.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["trackId"]), Index(value = ["playedAt"]), Index(value = ["scrobbled"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0L,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val scrobbled: Boolean = false
)
