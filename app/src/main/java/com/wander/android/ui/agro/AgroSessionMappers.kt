package com.wander.android.ui.agro

import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroHandoffState

/**
 * Whether a handoff describes the track this device already has loaded.
 *
 * Matched on title and artist rather than on the uri: the two devices may have reached the same
 * recording through different backends, and a uri from someone else's Navidrome never equals one
 * of ours even when it is the same song.
 */
internal fun AgroHandoffState.isSameTrackAs(track: UnifiedTrack?): Boolean {
    val here = track ?: return false
    return trackTitle.equals(here.title, ignoreCase = true) &&
        artistName.equals(here.artist, ignoreCase = true)
}

/** The live frame's shape, as the API models it. Same fields, two layers that do not import each other. */
internal fun com.wander.android.data.sources.agro.AgroJamNowPlaying.toApi() =
    com.wander.android.data.sources.agro.JamNowPlaying(
        trackId = trackId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        positionMs = positionMs,
        // The frame is an instruction to play, not a description of the room's opinion of the
        // track. Skip counts come from the jam itself, which is re-read on the same event.
        skipVotes = 0,
        skipsNeeded = 1,
        youSkipped = false,
        addedBy = addedBy,
        deviceId = deviceId,
        contentHash = contentHash,
        peerLanAddress = peerLanAddress,
        peerLanToken = peerLanToken
    )
