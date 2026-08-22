package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A friend, cached so the Friends screen has something to draw before the network answers.
 *
 * **Deliberately holds nothing about what they are playing.** Presence lives in memory only: it is
 * someone else's listening, it changes every few minutes, and — the part that matters — the switch
 * that permits it can be withdrawn. A row on this device's disk would outlive that withdrawal, so
 * there is no row.
 *
 * What is here is what a friend has already published as their public card, and what the app needs
 * in order to show a list at all while offline.
 */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val username: String,
    val displayName: String?,
    val bio: String?,
    val avatarUrl: String?,
    /** `accepted` or `pending` — the same wire values the server uses. */
    val state: String,
    /** True when we sent an unanswered request. Only meaningful while `state` is `pending`. */
    val outgoing: Boolean,
    /** The friend's own switches, cached so the UI can explain a blank profile without a round trip. */
    val showNowPlaying: Boolean,
    val showStats: Boolean,
    val syncedAt: Long
)
