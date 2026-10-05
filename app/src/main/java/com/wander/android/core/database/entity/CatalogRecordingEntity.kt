package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recording the Agro catalogue told this device about, kept whether or not the library holds
 * it.
 *
 * The point of trading fingerprints and lyrics is that a device need not have *heard* a recording
 * to know it. Applying an entry only to the tracks it matches threw away every recording the
 * library lacked, and the catalogue cursor had already moved past it. This keeps them all, so a
 * lyric search and a microphone match can name a song nobody here has played.
 *
 * [vector] and [centroid] are packed like `TrackEmbeddingEntity`'s, so the matcher scores both
 * with the same code. Only entries from the embedder this build runs are stored.
 */
@Entity(tableName = "catalog_recordings")
data class CatalogRecordingEntity(
    @PrimaryKey val recordingId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val vector: ByteArray,
    val centroid: ByteArray,
    val dim: Int,
    val model: String,
    val version: Int,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val lyricsSource: String?,
    /** Namespaced ids known to hold this audio, one per line. Never a `local:` id. */
    val sources: String,
    val updatedAt: Long
) {
    // Room generates neither for a ByteArray field; a row is the same row when its id and stamp are.
    override fun equals(other: Any?): Boolean =
        this === other || (other is CatalogRecordingEntity && recordingId == other.recordingId && updatedAt == other.updatedAt)

    override fun hashCode(): Int = 31 * recordingId.hashCode() + updatedAt.hashCode()
}
