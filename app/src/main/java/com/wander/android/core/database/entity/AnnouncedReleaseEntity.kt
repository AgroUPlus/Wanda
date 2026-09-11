package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A release this device has already told the user about.
 *
 * The release job walks forward from a watermark — the highest catalogue position it has been told
 * about — which is enough to avoid repeating a batch, but only while that position keeps meaning the
 * same thing. It does not survive the catalogue being republished: `updatedAt` records *when the row
 * was published*, not when the record came out, so re-indexing a server restamps an artist's entire
 * back catalogue above the stored watermark and every album they ever made arrives as news. That is
 * what happened, and a cursor alone cannot tell the two cases apart.
 *
 * The recording id can. It is the same id before and after a republish, so a row here is a durable
 * statement that this particular release has been announced once, whatever position it is currently
 * sitting at. The watermark still does the paging; this decides what is worth saying.
 *
 * Rows are written for primed releases too — the silent first run — so that following an artist and
 * then migrating a server does not turn their back catalogue into a notification storm.
 */
@Entity(tableName = "announced_releases")
data class AnnouncedReleaseEntity(
    @PrimaryKey val recordingId: String,
    val announcedAt: Long = System.currentTimeMillis()
)
