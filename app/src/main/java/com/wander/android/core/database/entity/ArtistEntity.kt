package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What we already know about an artist, so their page does not start from nothing twice.
 *
 * Opening an artist ran a cross-source search *and* fetched their page, every time, behind a
 * skeleton — so returning to someone you had looked at a minute earlier cost exactly as much as
 * meeting them for the first time. The screen has real content in Room already; what it lacked was
 * the three facts that make that content safe to show: who this artist *is*, what they look like,
 * and whether we have ever successfully asked.
 *
 * Deliberately not the shelves. Their arrangement is editorial and belongs to the backend, and
 * serialising a nested section tree into a column to save one fetch would be a schema to maintain
 * for a saving the network already makes cheaply. What is cached is the identity — and identity is
 * the part the page cannot render *correctly* without, since two artists can share a name.
 */
@Entity(tableName = "artists")
data class ArtistEntity(
    /**
     * The artist's name, lowercased.
     *
     * The key is the name because the route is: nothing else is known when the screen opens. Case
     * is folded for the same reason Room's other artist queries fold it — one artist reaches us
     * capitalised differently by different backends.
     */
    @PrimaryKey val nameKey: String,
    /** The display name as last seen, so the header need not use the route's spelling. */
    val name: String,
    /** The backend's id. Null when no source with artist pages recognised them. */
    val artistId: String?,
    val imageUrl: String?,
    val bio: String?,
    /** When the backend was last asked. Drives the freshness check in `CatalogRepository`. */
    val fetchedAt: Long
)
