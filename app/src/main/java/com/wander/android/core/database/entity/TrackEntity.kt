package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

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
        Index(value = ["isLibrary"])
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
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
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
        isLiked = isLiked,
        isCached = isCached,
        isDownloaded = isDownloaded,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp
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
        format = format
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
            isLiked = track.isLiked,
            isCached = track.isCached,
            isDownloaded = track.isDownloaded,
            isLibrary = isLibrary,
            localFilePath = localFilePath,
            playCount = track.playCount,
            lastPlayedTimestamp = track.lastPlayedTimestamp
        )
    }
}
