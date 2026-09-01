package com.wander.android.core.database.entity

import androidx.room.Entity

/**
 * One track's melodic shape, as the two-bytes-per-note blob [com.wander.android.core.audio.melody.MelodyContour] writes.
 *
 * Its own table rather than a column on `tracks`, for the reason every measured thing in this
 * database is: `tracks` is rewritten from the backend on every refetch, and this costs a full
 * decode to produce.
 *
 * [version] marks the extractor contract, as it does for the acoustic vectors. Change how notes
 * are segmented and every stored contour is a different alphabet from the ones being searched.
 */
@Entity(tableName = "melody_contours", primaryKeys = ["trackId"])
data class MelodyContourEntity(
    val trackId: String,
    val contour: ByteArray,
    val version: Int,
    val indexedAt: Long
) {
    // Room requires these for a ByteArray field: the generated equals would compare references and
    // silently report two identical contours as different.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MelodyContourEntity) return false
        return trackId == other.trackId &&
            version == other.version &&
            indexedAt == other.indexedAt &&
            contour.contentEquals(other.contour)
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + contour.contentHashCode()
        result = 31 * result + version
        result = 31 * result + indexedAt.hashCode()
        return result
    }
}
