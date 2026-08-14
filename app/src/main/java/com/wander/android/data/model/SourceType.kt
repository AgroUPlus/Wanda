package com.wander.android.data.model

import kotlinx.serialization.Serializable

/**
 * `idPrefix` namespaces track ids across backends, so `navidrome:42` and `local:42` never
 * collide in Room or in the playback queue.
 *
 * `priority` orders sources when the same recording is available from several of them — lower
 * wins. Local is instant, free and works offline; Navidrome is your own server at full quality;
 * Archive ranks last because its holdings are mostly live sets and transfers of variable fidelity.
 *
 * `isPersonalLibrary` says whether this backend's catalogue *is* the user's own collection.
 * Browsing Navidrome albums shows music you own; browsing Archive does not. It decides whether a
 * fetched track counts towards the Library screen — see `TrackDao.getAllTracksFlow`.
 */
@Serializable
enum class SourceType(
    val displayName: String,
    val idPrefix: String,
    val priority: Int,
    val isPersonalLibrary: Boolean
) {
    LOCAL("On this device", "local:", priority = 0, isPersonalLibrary = true),
    NAVIDROME("Navidrome", "navidrome:", priority = 1, isPersonalLibrary = true),
    YTMUSIC("YouTube Music", "ytm:", priority = 2, isPersonalLibrary = false),
    INTERNET_ARCHIVE("Internet Archive", "archive:", priority = 3, isPersonalLibrary = false)
}
