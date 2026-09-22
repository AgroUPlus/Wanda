package com.wander.android.core.backup

import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.ReplayRecapEntity
import kotlinx.serialization.Serializable

/**
 * The Room half of a backup: what was played, and the recaps made from it.
 *
 * Until Agro Replay a backup was preferences only, which made a restore quietly lossy — everything
 * about *settings* came back and a lifetime of listening did not. A recap that can outlive its
 * plays makes that worse rather than better, so both travel now.
 */

/**
 * One play: a track id and a time, and nothing else.
 *
 * Deliberately not the joined shape a screen reads. The title, artist and album belong to `tracks`,
 * which the restoring device has its own copy of — carrying them here would triple the file to
 * restate what is already on the other side. A play whose track is no longer in the library simply
 * does not show up in the history screen, exactly as it would not have before the backup.
 */
@Serializable
internal data class BackupPlay(
    val trackId: String,
    val playedAt: Long
)

/** One saved recap, carried whole so a restored device can still open that year. */
@Serializable
internal data class BackupRecap(
    val year: Int,
    val generatedAt: Long,
    val isFleetWide: Boolean,
    val totalMinutes: Long,
    val totalPlays: Long,
    val longestStreakDays: Int,
    val newArtistsCount: Int,
    val payloadJson: String
)

internal fun BackupRecap.toEntity(): ReplayRecapEntity = ReplayRecapEntity(
    year = year,
    generatedAt = generatedAt,
    isFleetWide = isFleetWide,
    totalMinutes = totalMinutes,
    totalPlays = totalPlays,
    longestStreakDays = longestStreakDays,
    newArtistsCount = newArtistsCount,
    payloadJson = payloadJson
)

internal fun ReplayRecapEntity.toBackup(): BackupRecap = BackupRecap(
    year = year,
    generatedAt = generatedAt,
    isFleetWide = isFleetWide,
    totalMinutes = totalMinutes,
    totalPlays = totalPlays,
    longestStreakDays = longestStreakDays,
    newArtistsCount = newArtistsCount,
    payloadJson = payloadJson
)

/**
 * A restored play, marked as already reported.
 *
 * `scrobbled` and `agroSynced` are both set: these plays were reported by whichever device made the
 * backup, and a restore that left them pending would re-upload years of history to Navidrome and to
 * Agro the first time the sync worker ran.
 */
internal fun BackupPlay.toEntity(): HistoryEntity = HistoryEntity(
    trackId = trackId,
    playedAt = playedAt,
    scrobbled = true,
    agroSynced = true
)
