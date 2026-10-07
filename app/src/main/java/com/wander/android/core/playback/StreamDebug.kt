package com.wander.android.core.playback

import com.wander.android.data.sources.StreamRoute

/**
 * What the debug overlay may know about a resolved stream. Deliberately not the URL: only the host
 * is kept, because a stream URL carries tokens that must never reach a screen or a log.
 */
data class StreamDebug(
    val route: StreamRoute,
    val client: String?,
    val format: String,
    val bitRateKbps: Int,
    val host: String?
)
