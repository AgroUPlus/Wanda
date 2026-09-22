package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A year's Agro Replay, kept after the plays behind it are gone.
 *
 * This is the point of the table. At the end of the story the user may accept an offer to forget
 * the individual plays from the years already summarised — and a recap computed from rows that no
 * longer exist cannot be computed again. So it is written down once, before anything is deleted,
 * and it is the durable record of that year from then on.
 *
 * ## Why one JSON column and not twenty
 *
 * [payloadJson] holds the whole `ReplayReport`. A recap is a *document*: nothing queries it by
 * field, nothing joins to it, and it is read whole or not at all. Modelling the top artists, tracks,
 * albums, genres and the circle as child tables would buy nothing and cost four more entities, four
 * more DAOs and a migration every time a card is added.
 *
 * The scalars beside it are lifted out precisely because they *are* queried: they are what the
 * lifetime totals are summed from, which is what makes forgetting the plays safe.
 */
@Entity(tableName = "replay_recaps")
data class ReplayRecapEntity(
    /** One recap per year, so saving the same year twice replaces rather than duplicates. */
    @PrimaryKey val year: Int,
    val generatedAt: Long,
    /** Whether it covered the whole Agro account or only this device, at the time it was made. */
    val isFleetWide: Boolean,
    val totalMinutes: Long,
    val totalPlays: Long,
    val longestStreakDays: Int,
    val newArtistsCount: Int,
    val payloadJson: String
)
