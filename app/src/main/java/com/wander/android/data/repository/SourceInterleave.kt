package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * Round-robin over the sources a pool came from, one track at a time.
 *
 * Concatenating instead hands the whole station to whichever backend answered with the most
 * tracks: a pool of 40 YouTube Music rows and 6 Navidrome ones plays as a YouTube Music station
 * with a tail. Taking one from each in turn keeps every configured backend present from the start.
 *
 * Each source's own run is shuffled, so a station reseeded from the same library does not open with
 * the same three songs every time.
 */
internal fun interleaveBySource(tracks: List<UnifiedTrack>): List<UnifiedTrack> {
    val bySource: Map<SourceType, List<UnifiedTrack>> = tracks.groupBy { it.source }
    val queues = bySource.values.map { it.shuffled().toMutableList() }
    return buildList {
        while (queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> if (queue.isNotEmpty()) add(queue.removeAt(0)) }
        }
    }
}
