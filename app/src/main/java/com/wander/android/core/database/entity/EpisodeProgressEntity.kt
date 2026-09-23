package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How far into an episode the listener got.
 *
 * Only episodes are stored. A song that is stopped halfway is started again from the beginning
 * next time, which is what everyone expects of a song and what nobody expects of a two-hour
 * podcast — restarting one of those from zero is the single thing that makes a music player
 * unusable for spoken audio.
 *
 * [durationMs] is kept alongside the position so "how near the end is this" can be answered
 * without the track being to hand, which is what [com.wander.android.data.repository.EpisodeProgressRepository.stateOf] needs.
 */
@Entity(tableName = "episode_progress")
data class EpisodeProgressEntity(
    @PrimaryKey val trackId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)
