package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.local.EXTRA_ALBUM_ARTIST
import com.wander.android.data.sources.local.EXTRA_CONTENT_HASH
import com.wander.android.data.sources.local.EXTRA_EXTENSION
import com.wander.android.data.sources.local.EXTRA_SIZE_BYTES

/**
 * Room is the offline source of truth: whatever a source returns is persisted here and read back
 * as a Flow, so the library still works with no network.
 *
 * Note that `streamUri` is only stored for sources whose URLs are stable and credential-free
 * (local files, downloads). Navidrome and YouTube Music URLs carry auth or expire, so they are
 * resolved at playback time instead.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["source", "sourceTrackId"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["albumId"]),
        Index(value = ["isLiked"]),
        Index(value = ["isDownloaded"]),
        Index(value = ["isLibrary"]),
        Index(value = ["contentHash"])
    ]
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val sourceTrackId: String,
    val source: SourceType,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUri: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRateKbps: Int?,
    val format: String?,
    val isLive: Boolean = false,
    val isLiked: Boolean = false,
    val isCached: Boolean = false,
    val isDownloaded: Boolean = false,
    /**
     * True when the track belongs to the user's own collection, false when it was merely seen in
     * passing (a search hit, a radio pick, an Archive browse). The Library screen reads only rows
     * where this is set, which is what keeps searching from growing the library.
     */
    val isLibrary: Boolean = false,
    val localFilePath: String? = null,

    // ── Library sync ────────────────────────────────────────────────────────────────────────
    // Only meaningful for LOCAL tracks. All nullable: a track that has never been considered for
    // upload simply has none of it.

    /** File size in bytes, from MediaStore. The server needs it to plan a transfer. */
    val sizeBytes: Long? = null,
    /** Extension of the real filename, for the name the server files it under. */
    val fileExtension: String? = null,
    /** Album artist, which is what a compilation should be shelved by rather than track artist. */
    val albumArtist: String? = null,
    /**
     * SHA-256 of the file's bytes — the identity Agro's library index keys on.
     *
     * Filled in lazily by the hashing worker, not at scan time: hashing an entire library at once
     * would take minutes and burn battery for a feature the user may never turn on.
     */
    val contentHash: String? = null,
    /**
     * When the server confirmed it holds these exact bytes.
     *
     * This is what makes offering to delete the local copy safe — without a confirmation there is
     * no evidence the file exists anywhere else.
     */
    val syncedAt: Long? = null,

    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),

    // ── Indexer retry backoff ───────────────────────────────────────────────────────────────
    /** When this device last attempted to decode or index this track. */
    val lastAttemptAt: Long? = null,
    /** How many consecutive times decoding or indexing this track has failed. */
    val attempts: Int = 0
) {
    fun toUnifiedTrack() = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUri = localFilePath ?: streamUri,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRateKbps = bitRateKbps,
        format = format,
        isLive = isLive,
        isLiked = isLiked,
        isCached = isCached,
        isDownloaded = isDownloaded,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp,
        // Round-tripped through extraData so the sync layer can read them without every other
        // caller of UnifiedTrack paying for four more fields on the model.
        extraData = buildMap {
            sizeBytes?.let { put(EXTRA_SIZE_BYTES, it.toString()) }
            fileExtension?.let { put(EXTRA_EXTENSION, it) }
            albumArtist?.let { put(EXTRA_ALBUM_ARTIST, it) }
            contentHash?.let { put(EXTRA_CONTENT_HASH, it) }
        }
    )

    /** Backend metadata only — see [TrackSourceFields] for why user state is excluded. */
    fun toSourceFields() = TrackSourceFields(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUri = streamUri,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRateKbps = bitRateKbps,
        format = format,
        isLive = isLive
    )

    companion object {
        fun fromUnifiedTrack(
            track: UnifiedTrack,
            localFilePath: String? = null,
            isLibrary: Boolean = false
        ) = TrackEntity(
            id = track.id,
            sourceTrackId = track.id.substringAfter(':', track.id),
            source = track.source,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumId = track.albumId,
            artistId = track.artistId,
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            streamUri = track.streamUri,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            year = track.year,
            genre = track.genre,
            bitRateKbps = track.bitRateKbps,
            format = track.format,
            isLive = track.isLive,
            isLiked = track.isLiked,
            isCached = track.isCached,
            isDownloaded = track.isDownloaded,
            isLibrary = isLibrary,
            localFilePath = localFilePath,
            sizeBytes = track.extraData[EXTRA_SIZE_BYTES]?.toLongOrNull(),
            fileExtension = track.extraData[EXTRA_EXTENSION],
            albumArtist = track.extraData[EXTRA_ALBUM_ARTIST],
            contentHash = track.extraData[EXTRA_CONTENT_HASH],
            playCount = track.playCount,
            lastPlayedTimestamp = track.lastPlayedTimestamp
        )
    }
}
