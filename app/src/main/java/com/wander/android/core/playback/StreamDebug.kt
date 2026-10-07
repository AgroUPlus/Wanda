package com.wander.android.core.playback

import com.wander.android.data.sources.StreamRoute

/**
 * What the debug overlay may know about a track's last resolve. Deliberately not the URL: only the
 * host is kept, because a stream URL carries tokens that must never reach a screen or a log.
 *
 * A failed resolve has no [route]; [error] says why. Error messages are fixed strings built by
 * the resolvers and never contain a URL.
 */
data class StreamDebug(
    val route: StreamRoute?,
    val client: String?,
    val format: String?,
    val bitRateKbps: Int,
    val host: String?,
    val note: String? = null,
    val error: String? = null
)
