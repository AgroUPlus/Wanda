package com.wander.android.ui.components

import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackDeduplicator

/**
 * List keys that survive a track changing source.
 *
 * A cross-source page fills in as each backend answers, and deduplication then keeps whichever
 * copy ranks best. Keying on `track.id` means the moment a Navidrome copy displaces the YouTube
 * Music one the row is a *different* item: it disappears and a new one appears in its place, with
 * no animation to connect them. Keying on the recording instead makes that a swap in place.
 *
 * Returns one key per track, positionally. Repeats are suffixed, because
 * [TrackDeduplicator.recordingKey] is deliberately coarser than deduplication itself — two takes
 * of the same title with different lengths both survive, and duplicate keys crash a lazy list.
 */
internal fun trackListKeys(tracks: List<UnifiedTrack>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return tracks.map { track ->
        val key = TrackDeduplicator.recordingKey(track)
        val count = seen.merge(key, 1, Int::plus) ?: 1
        if (count == 1) key else "$key#$count"
    }
}
