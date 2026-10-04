package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recap of a jam this account was in, mirrored from Agro.
 *
 * Kept locally so the card is there offline and on a cold start, as everything Agro sends is: the
 * server is where a recap comes from, Room is where the screen reads it. [payloadJson] is the whole
 * `JamRecap` — a recap is read whole or not at all, exactly as `ReplayRecapEntity` reasons.
 */
@Entity(tableName = "jam_recaps")
data class JamRecapEntity(
    /** The server's id, so a dismissal can name it. */
    @PrimaryKey val id: String,
    /** RFC 3339, as the server wrote it; sorts correctly as text. */
    val createdAt: String,
    val payloadJson: String
)
