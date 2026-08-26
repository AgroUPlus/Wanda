package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A song a friend handed this account, cached so the inbox reads offline.
 *
 * The opposite decision from [FriendEntity]'s presence rule, and deliberately so. Presence is a
 * fact about someone else's current listening that they can withdraw, so it is never written down.
 * A drop is a message *addressed to this account*: it was given, it is meant to last, and an inbox
 * that emptied itself whenever the network was gone would not be an inbox.
 *
 * [readAt] is this account's own state, so it is real here. On a drop this account *sent* the server
 * blanks it, because whether somebody opened what you gave them is information about them — never
 * build a "seen" indicator on that field.
 */
/**
 * The index the inbox queries actually use.
 *
 * Declared here rather than only in the migration, which is the bug this comment exists to prevent
 * from coming back: Room validates the live schema against *this* class on every open, so an index
 * created by a migration but absent from the entity is a mismatch, and a mismatch throws while the
 * database is being opened — which is to say, on every launch.
 *
 * The column order follows the `WHERE` clauses in `DropDao`: filter on `incoming` and `archived`,
 * then read in `createdAt` order.
 */
@Entity(
    tableName = "drops",
    indices = [Index(value = ["incoming", "archived", "createdAt"])]
)
data class DropEntity(
    @PrimaryKey val id: String,
    val fromUser: String,
    val toUser: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?,
    /** Present only when the sender's copy is in the server's index. */
    val contentHash: String?,
    val trackUri: String?,
    val note: String?,
    val createdAt: String,
    val readAt: String?,
    val archived: Boolean,
    /**
     * The recipient's one-emoji reply, or null if they have not reacted.
     *
     * Unlike [readAt], this is meaningful on both sides. A read receipt is something the server
     * observed and deliberately keeps from the sender; a reaction is something the recipient
     * chose to send, so it comes back with a sent drop too.
     */
    val reaction: String?,
    /** True for the inbox, false for the sent list. Both live in this table. */
    val incoming: Boolean,
    val syncedAt: Long
)
