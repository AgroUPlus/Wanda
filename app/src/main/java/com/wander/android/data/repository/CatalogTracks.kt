package com.wander.android.data.repository

import com.wander.android.core.database.dao.CatalogRecordingDao
import com.wander.android.core.database.entity.CatalogRecordingEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A catalogue recording as something that can be named on screen. Never playable: its source is
 * [SourceType.UNRESOLVED] until [CatalogTrackResolver] finds a backend that has it.
 */
internal fun CatalogRecordingEntity.toStubTrack() = UnifiedTrack(
    id = CatalogRecordingDao.PREFIX + recordingId,
    source = SourceType.UNRESOLVED,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs
)

/** Finds the track in the user's active sources that a [toStubTrack] stub names. */
@Singleton
class CatalogTrackResolver @Inject constructor(private val trackMatcher: TrackMatcher) {

    /** Null when no active source has it — a normal outcome for a song nobody here can play yet. */
    suspend fun playable(stub: UnifiedTrack): UnifiedTrack? =
        trackMatcher.match(stub.title, stub.artist, stub.durationMs)
}
