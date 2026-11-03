package com.wander.android.core.backup

import com.wander.android.core.database.entity.CanonicalMetadataEntity
import com.wander.android.core.database.entity.JamRecapEntity
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import com.wander.android.core.database.entity.TrackFeatureEntity
import java.util.Base64
import kotlinx.serialization.Serializable

/**
 * The sections added after the first backups: catalogue corrections, shared-playlist copies, jam
 * recaps, and the two measurements taken from the audio itself.
 *
 * Every one is its own [BackupSection] with its own digest, and every field in [BackupDocument]
 * that holds them defaults to empty, so a backup made before they existed neither encodes nor
 * lists them — it verifies and restores exactly as it did.
 */

/** A correction from the shared catalogue; see [CanonicalMetadataEntity]. */
@Serializable
internal data class BackupCanonical(
    val trackId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val recordingId: String,
    val updatedAt: Long
)

internal fun CanonicalMetadataEntity.toBackup() =
    BackupCanonical(trackId, title, artist, album, recordingId, updatedAt)

internal fun BackupCanonical.toEntity() =
    CanonicalMetadataEntity(trackId, title, artist, album, recordingId, updatedAt)

/**
 * A shared playlist's local copy, with the tracks the server confirmed. Edits not yet sent — the
 * op queue and its `pending:` items — are left out: replayed from another install they could be
 * sent twice, and the device that made them still holds them.
 */
@Serializable
internal data class BackupSharedPlaylist(
    val agroId: String,
    val localPlaylistId: String? = null,
    val title: String,
    val description: String? = null,
    val ownerId: String,
    val visibility: String,
    val editAccess: String,
    val myRole: String,
    val revision: Long,
    val publishedAt: Long,
    val isBlend: Boolean = false,
    val items: List<BackupSharedItem> = emptyList()
)

@Serializable
internal data class BackupSharedItem(
    val itemId: String,
    val position: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val addedBy: String? = null,
    val addedAt: String? = null
)

internal fun SharedPlaylistEntity.toBackup(items: List<SharedPlaylistItemEntity>) = BackupSharedPlaylist(
    agroId = agroId, localPlaylistId = localPlaylistId, title = title, description = description,
    ownerId = ownerId, visibility = visibility, editAccess = editAccess, myRole = myRole,
    revision = revision, publishedAt = publishedAt, isBlend = isBlend,
    items = items.filterNot { it.isPending }.map {
        BackupSharedItem(it.itemId, it.position, it.trackId, it.title, it.artist, it.album, it.durationMs, it.addedBy, it.addedAt)
    }
)

/** Restored as [syncState], so the next sync checks the copy against the server before trusting it. */
internal fun BackupSharedPlaylist.toEntity(syncState: String) = SharedPlaylistEntity(
    agroId = agroId, localPlaylistId = localPlaylistId, title = title, description = description,
    ownerId = ownerId, visibility = visibility, editAccess = editAccess, myRole = myRole,
    revision = revision, syncState = syncState, lastSyncedAt = 0L, publishedAt = publishedAt, isBlend = isBlend
)

internal fun BackupSharedPlaylist.itemEntities() = items.map {
    SharedPlaylistItemEntity(agroId, it.itemId, it.position, it.trackId, it.title, it.artist, it.album, it.durationMs, it.addedBy, it.addedAt)
}

@Serializable
internal data class BackupJamRecap(val id: String, val createdAt: String, val payloadJson: String)

internal fun JamRecapEntity.toBackup() = BackupJamRecap(id, createdAt, payloadJson)
internal fun BackupJamRecap.toEntity() = JamRecapEntity(id, createdAt, payloadJson)

/** One track's acoustic vector; six floats, so the whole table travels. */
@Serializable
internal data class BackupFeatures(
    val trackId: String,
    val tempo: Float,
    val energy: Float,
    val brightness: Float,
    val danceability: Float,
    val keyX: Float,
    val keyY: Float,
    val version: Int,
    val measuredAt: Long
)

internal fun TrackFeatureEntity.toBackup() =
    BackupFeatures(trackId, tempo, energy, brightness, danceability, keyX, keyY, version, measuredAt)

internal fun BackupFeatures.toEntity() =
    TrackFeatureEntity(trackId, tempo, energy, brightness, danceability, keyX, keyY, version, measuredAt)

/**
 * One track's neural fingerprint, its bytes in Base64. Already int8-quantised by the embedder, so
 * there is nothing further to squeeze out of them before the whole document is gzipped.
 */
@Serializable
internal data class BackupEmbedding(
    val trackId: String,
    val vector: String,
    val centroid: String? = null,
    val dim: Int,
    val model: String,
    val version: Int,
    val computedAt: Long
)

private val encoder = Base64.getEncoder()
private val decoder = Base64.getDecoder()

internal fun TrackEmbeddingEntity.toBackup() = BackupEmbedding(
    trackId = trackId,
    vector = encoder.encodeToString(vector),
    centroid = centroid?.let { encoder.encodeToString(it) },
    dim = dim, model = model, version = version, computedAt = computedAt
)

/** @throws IllegalArgumentException if the Base64 is damaged — never restored as a blank vector. */
internal fun BackupEmbedding.toEntity() = TrackEmbeddingEntity(
    trackId = trackId,
    vector = decoder.decode(vector),
    centroid = centroid?.let { decoder.decode(it) },
    dim = dim, model = model, version = version, computedAt = computedAt
)
