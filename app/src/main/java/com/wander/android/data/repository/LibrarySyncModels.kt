package com.wander.android.data.repository

import com.wander.android.data.sources.agro.SyncRoute

/**
 * Where a *download* run got to.
 *
 * Separate from [SyncProgress], which is the upload direction. Keyed on content hash rather than
 * on position, so the detail list can mark each track done, in flight, or still waiting without
 * caring what order they were fetched in.
 */
data class FetchProgress(
    val done: Set<String> = emptySet(),
    val current: String? = null,
    val total: Int = 0,
    /** How the track in flight is actually travelling. Null until the stream opens. */
    val route: SyncRoute? = null
) {
    val running: Boolean get() = current != null
}

/** Where a sync run got to, for the Settings screen. */
data class SyncProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentTitle: String? = null
)
