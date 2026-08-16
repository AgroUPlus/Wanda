package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per play. Doubles as two independent outboxes.
 *
 * `scrobbled` is about the *music source* — Navidrome's own play counts. `agroSynced` is about the
 * fleet's shared statistics. They are separate flags because they are separate destinations that
 * fail separately: a play accepted by Navidrome while Agro was unreachable has to stay pending for
 * Agro, and one flag for both meant whichever succeeded first cancelled the other.
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playedAt"]),
        Index(value = ["scrobbled"]),
        Index(value = ["agroSynced"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0L,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val scrobbled: Boolean = false,
    val agroSynced: Boolean = false
)
