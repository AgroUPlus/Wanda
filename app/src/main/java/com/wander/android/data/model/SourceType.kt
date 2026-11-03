package com.wander.android.data.model

import kotlinx.serialization.Serializable

/**
 * `idPrefix` namespaces track ids across backends, so `navidrome:42` and `local:42` never
 * collide in Room or in the playback queue.
 *
 * `priority` orders sources when the same recording is available from several of them — lower
 * wins. Local is instant, free and works offline; Navidrome is your own server at full quality.
 *
 * `displayName` is the fixed English name, for logs and diagnostics. What the user reads is
 * [nameRes] (and [shortNameRes] where room is tight), so it can be translated.
 *
 * `isPersonalLibrary` says whether this backend's catalogue *is* the user's own collection.
 * Browsing Navidrome albums shows music you own. It decides whether a fetched track counts
 * towards the Library screen — see `TrackDao.getAllTracksFlow`.
 */
@Serializable
enum class SourceType(
    val displayName: String,
    val idPrefix: String,
    val priority: Int,
    val isPersonalLibrary: Boolean
) {
    LOCAL("On this device", "local:", priority = 0, isPersonalLibrary = true),
    NAVIDROME("Subsonic", "navidrome:", priority = 1, isPersonalLibrary = true),
    YTMUSIC("YouTube Music", "ytm:", priority = 2, isPersonalLibrary = false),
    DEEZER("Deezer", "deezer:", priority = 3, isPersonalLibrary = false),
    PODCAST("Podcasts", "podcast:", priority = 4, isPersonalLibrary = false),

    /**
     * A playlist someone shared through the paired Agro server and this account follows. Holds
     * playlists only, never tracks: each of its tracks is matched to one of the sources above, or
     * is [UNRESOLVED] until it is.
     */
    AGRO("Shared on Agro", "agro:", priority = 98, isPersonalLibrary = false),

    /**
     * A track imported from a pasted playlist that no backend has been matched to yet. It has no
     * `IMusicSource`, is never playable and never a search candidate; `PlaylistImportWorker`
     * replaces it with the matched track.
     */
    UNRESOLVED("Importing", "unresolved:", priority = 99, isPersonalLibrary = false)
}
