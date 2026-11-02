package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A like or unlike made in Wanda that its backend has not confirmed yet. One row per track: a
 * second toggle before the first was sent replaces it, so only the latest wish is sent.
 *
 * [queuedAt] identifies the wish, so a send that finishes after the user toggled again clears
 * nothing it did not send.
 */
@Entity(tableName = "pending_likes")
data class PendingLikeEntity(
    @PrimaryKey val trackId: String,
    val liked: Boolean,
    val attempts: Int,
    val queuedAt: Long
)
