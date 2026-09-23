package com.wander.android.core.backup

import com.wander.android.core.database.entity.EpisodeProgressEntity
import com.wander.android.core.database.entity.PlaylistEntity
import com.wander.android.core.database.entity.RecordingLinkEntity
import com.wander.android.core.database.entity.RecordingSplitEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.serialization.Serializable

/**
 * The parts of the library only this device knows: which tracks were liked or added, how often
 * they were played, the playlists made here, the duplicate-recording decisions, and where each
 * podcast episode was left.
 *
 * Caches — artwork, lyrics, audio features, embeddings, shelves, server-synced friends and drops —
 * are left out on purpose. They are rebuilt from their sources on the next sync, and carrying them
 * would make the file many times larger to restate what the other side can fetch again.
 */

/**
 * A track with its user state, carried whole so a play or playlist entry restored onto a fresh
 * install still resolves to a title before the library has synced. Download and cache flags are
 * not carried: the file behind them is not in the backup.
 */
@Serializable
internal data class BackupTrack(
    val id: String,
    val sourceTrackId: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val streamUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRateKbps: Int? = null,
    val format: String? = null,
    val isLive: Boolean = false,
    val isEpisode: Boolean = false,
    val isLiked: Boolean = false,
    val isLibrary: Boolean = false,
    val localFilePath: String? = null,
    val contentHash: String? = null,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null,
    val addedTimestamp: Long
)

@Serializable
internal data class BackupPlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val coverArtUrl: String? = null,
    val trackIds: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** A pair of recordings the user said are, or are not, the same song. */
@Serializable
internal data class BackupRecordingPair(
    val idA: String,
    val idB: String,
    val at: Long,
    /** Only meaningful for links: how alike the two were judged. */
    val similarity: Double = 0.0
)

@Serializable
internal data class BackupEpisode(
    val trackId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

internal fun TrackEntity.toBackup(): BackupTrack = BackupTrack(
    id = id, sourceTrackId = sourceTrackId, source = source.name, title = title, artist = artist,
    album = album, albumId = albumId, artistId = artistId, albumArtist = albumArtist,
    durationMs = durationMs, artworkUrl = artworkUrl, streamUri = streamUri,
    trackNumber = trackNumber, discNumber = discNumber, year = year, genre = genre,
    bitRateKbps = bitRateKbps, format = format, isLive = isLive, isEpisode = isEpisode, isLiked = isLiked,
    isLibrary = isLibrary, localFilePath = localFilePath, contentHash = contentHash,
    playCount = playCount, lastPlayedTimestamp = lastPlayedTimestamp,
    addedTimestamp = addedTimestamp
)

/** Null for a source this build does not know — a newer build's backend is skipped, not guessed. */
internal fun BackupTrack.toEntity(): TrackEntity? {
    val sourceType = SourceType.entries.firstOrNull { it.name == source } ?: return null
    return TrackEntity(
        id = id, sourceTrackId = sourceTrackId, source = sourceType, title = title,
        artist = artist, album = album, albumId = albumId, artistId = artistId,
        durationMs = durationMs, artworkUrl = artworkUrl, streamUri = streamUri,
        trackNumber = trackNumber, discNumber = discNumber, year = year, genre = genre,
        bitRateKbps = bitRateKbps, format = format, isLive = isLive, isEpisode = isEpisode, isLiked = isLiked,
        isLibrary = isLibrary, localFilePath = localFilePath, albumArtist = albumArtist,
        contentHash = contentHash, playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp, addedTimestamp = addedTimestamp
    )
}

internal fun PlaylistEntity.toBackup() =
    BackupPlaylist(id, name, comment, coverArtUrl, trackIds, createdAt, updatedAt)

internal fun BackupPlaylist.toEntity() =
    PlaylistEntity(id, name, comment, coverArtUrl, trackIds, createdAt, updatedAt)

internal fun RecordingSplitEntity.toBackup() = BackupRecordingPair(idA, idB, pinnedAt)
internal fun BackupRecordingPair.toSplit() = RecordingSplitEntity(idA, idB, at)
internal fun RecordingLinkEntity.toBackup() = BackupRecordingPair(idA, idB, linkedAt, similarity)
internal fun BackupRecordingPair.toLink() = RecordingLinkEntity(idA, idB, similarity, at)

internal fun EpisodeProgressEntity.toBackup() =
    BackupEpisode(trackId, positionMs, durationMs, updatedAt)

internal fun BackupEpisode.toEntity() =
    EpisodeProgressEntity(trackId, positionMs, durationMs, updatedAt)
