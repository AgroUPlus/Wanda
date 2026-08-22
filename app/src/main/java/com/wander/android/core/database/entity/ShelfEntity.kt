package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recommendation shelf as last fetched from a backend.
 *
 * Home's recommendation shelves used to be fetched live on every refresh and raced against a
 * timeout, so a slow network produced a *different* front page each launch — the feed looked
 * unreliable rather than merely slow. Persisting the shelf structure makes the last good feed the
 * thing Home shows, with the network only ever replacing it.
 *
 * Only the track *ids* are stored. The tracks themselves already live in `tracks`, written by
 * `RecommendationRepository`, and duplicating them here would give a shelf its own stale copy of a
 * title and artwork that the library row has a fresher version of.
 */
@Entity(tableName = "shelves")
data class ShelfEntity(
    /** Stable across fetches — derived from the shelf's title, not its position. */
    @PrimaryKey val id: String,
    val title: String,
    /** Where this shelf sat in the feed, so the order the backend chose survives a reload. */
    val position: Int,
    /** Track ids in order, comma-separated. Ids never contain a comma. */
    val trackIds: String,
    val fetchedAt: Long
)
