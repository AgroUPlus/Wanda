package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Where an RSS episode's chapters and transcript live, if its feed names them (Podcasting 2.0).
 *
 * Only the addresses are stored: the files are fetched when the listener opens them, so a sync
 * stays a single small download per feed. One row per episode that has either.
 */
@Entity(tableName = "episode_extras")
data class EpisodeExtrasEntity(
    @PrimaryKey val trackId: String,
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    /** The transcript's format, as the feed declared it; see `TranscriptParser.formatOf`. */
    val transcriptMime: String?
)
