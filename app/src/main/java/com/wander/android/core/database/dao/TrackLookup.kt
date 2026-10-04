package com.wander.android.core.database.dao

import com.wander.android.core.database.entity.TrackEntity

/**
 * SQLite on this app's oldest supported Android binds at most 999 values in one statement, and a
 * playlist can hold more tracks than that. Below the limit this is the one query it always was.
 */
private const val MAX_BOUND_IDS = 900

/** [TrackLibraryQueries.getTracksByIds], for a list of ids of any length. */
suspend fun TrackLibraryQueries.getTracksByIdsChunked(ids: List<String>): List<TrackEntity> =
    ids.distinct().chunked(MAX_BOUND_IDS).flatMap { getTracksByIds(it) }
