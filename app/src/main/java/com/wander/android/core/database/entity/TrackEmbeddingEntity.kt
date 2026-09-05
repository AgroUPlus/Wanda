package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * One track's neural audio fingerprint: the sequence of segment embedding vectors the CNN
 * (`models/wanda_embedder.tflite`) produces, packed big-endian float32, [dim] values per segment.
 *
 * Its own table for the reason every measured thing here is: `tracks` is rewritten from the
 * backend on every refetch, and this costs a full decode to produce. It replaced the landmark
 * `fingerprints` index — one BLOB row against ~18k landmark rows.
 *
 * [model] and [version] together mark the contract: a vector from a different model, or a
 * different segmentation, is a different alphabet from the ones a query is compared against, so
 * it is treated as absent and recomputed rather than mixed in. The desktop indexer writes the
 * same three fields (`core/embedder.py`).
 */
@Entity(tableName = "track_embeddings", primaryKeys = ["trackId"])
data class TrackEmbeddingEntity(
    val trackId: String,
    /** `nSegments * dim` float32 values, big-endian, row-major (segment-major). */
    val vector: ByteArray,
    /**
     * The mean of [vector]'s segments, L2-normalised — one `dim`-value summary of the whole track,
     * packed the same way.
     *
     * A search index, and the reason a match does not have to read [vector] at all for most of the
     * library. Comparing a clip's mean against 1400 of these is 180,000 multiply-adds and reads
     * 700 KB; comparing it against the segments themselves is 232 million and reads 84 MB, which
     * is where every second of a recognition used to go. The full comparison then runs on the
     * handful of tracks this shortlists — see `EmbeddingRepository.match`.
     *
     * Null on a row written before this column existed. Such a row is shortlisted unconditionally
     * rather than skipped, so the index is never silently incomplete while the backfill runs.
     */
    val centroid: ByteArray? = null,
    val dim: Int,
    val model: String,
    val version: Int,
    val computedAt: Long
) {
    // Room generates neither equals nor hashCode for a ByteArray field, and the default compares
    // the array by identity — two identical embeddings would read as different.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackEmbeddingEntity) return false
        return trackId == other.trackId &&
            dim == other.dim &&
            model == other.model &&
            version == other.version &&
            computedAt == other.computedAt &&
            vector.contentEquals(other.vector) &&
            centroid.contentEquals(other.centroid)
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + (centroid?.contentHashCode() ?: 0)
        result = 31 * result + dim
        result = 31 * result + model.hashCode()
        result = 31 * result + version
        result = 31 * result + computedAt.hashCode()
        return result
    }
}
