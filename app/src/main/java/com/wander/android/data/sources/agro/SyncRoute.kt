package com.wander.android.data.sources.agro

/**
 * How a track actually travelled.
 *
 * Decided by [AgroUploader.fetchP2POrRelay] as it tries each path in turn, and reported back so
 * the UI can say what happened rather than what was hoped for. The card used to name the route
 * from `peerSources` *before* anything was attempted, so it said "Direct Wi-Fi" while every
 * transfer was quietly falling back to the relay — or failing.
 */
enum class SyncRoute(val label: String) {
    /** Straight to the peer over the local network. */
    DIRECT("Direct Wi-Fi"),

    /** Through the server, which is passing bytes between two devices that cannot see each other. */
    RELAY("Relay"),

    /** From the server's own copy of the file. */
    ARCHIVE("Server archive")
}

/** A stream being fetched, and the path it came by. */
data class FetchedStream(
    val response: okhttp3.Response,
    val route: SyncRoute
)
