package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One track's canonical fingerprint, as computed from its audio.
 *
 * Distinct from `fingerprints`, which holds the landmark index used to recognise music through a
 * microphone. That answers "what is playing in the room"; this answers "which recording is this
 * file", and the two use different algorithms because they are asked different questions.
 *
 * The sequence is stored whole, because matching is decided on it: the sub-hash index below only
 * proposes candidates, and the confirmation compares the sequences.
 */
@Entity(tableName = "recording_fingerprints")
data class RecordingFingerprintEntity(
    @PrimaryKey val trackId: String,
    /**
     * The sub-hash sequence, four bytes per entry, big-endian.
     *
     * A blob rather than a delimited string: it is read to be compared, never queried on, and at
     * roughly 30 hashes a second a text encoding would cost more than the audio's own metadata.
     */
    val subHashes: ByteArray,
    /** Milliseconds of audio this was computed from, for a cheap first rejection. */
    val durationMs: Long,
    val computedAt: Long
) {
    // Room generates neither for a ByteArray, and the default would compare identity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordingFingerprintEntity) return false
        return trackId == other.trackId &&
            subHashes.contentEquals(other.subHashes) &&
            durationMs == other.durationMs &&
            computedAt == other.computedAt
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + subHashes.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + computedAt.hashCode()
        return result
    }
}

/**
 * One half of one sub-hash, as an index into [RecordingFingerprintEntity].
 *
 * Halves rather than whole sub-hashes, and this is the whole reason the table exists. A single
 * flipped bit changes a 32-bit sub-hash completely, so an index keyed on whole ones finds a file
 * that was re-containered or re-levelled and finds *nothing* once the audio has been through a
 * lossy encoder — measured at zero surviving sub-hashes against a copy that still scored well
 * above chance on the sequence comparison. Split in two, one damaged half leaves the other
 * intact, and a degraded copy still lands enough exact hits to be proposed.
 *
 * So this is a candidate filter and nothing more. Hits here are counted, not trusted; a match is
 * decided by comparing the full sequences.
 */
@Entity(
    tableName = "recording_sub_hashes",
    primaryKeys = ["half", "trackId"],
    indices = [Index("trackId")]
)
data class RecordingSubHashEntity(
    /**
     * The 16-bit half, tagged with which end it came from.
     *
     * Tagged because the two halves share a value space: without it, the low half of one hash
     * would collide with the high half of another and quietly inflate a candidate's score.
     */
    val half: Int,
    val trackId: String
)
