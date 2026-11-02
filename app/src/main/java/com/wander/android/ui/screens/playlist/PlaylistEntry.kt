package com.wander.android.ui.screens.playlist

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.UnifiedTrack

/**
 * One row of a playlist as the screen draws it: the track, where it sits in the playlist as last
 * read, and a key that names this entry rather than this position.
 *
 * The rows were keyed `id_index`, which changes the moment a row moves — a drag reorder needs the
 * dragged row to keep its key all the way to where it is dropped. The track id alone is not
 * enough, since a playlist may hold one song twice, so the key is the id and which copy it is.
 */
@Immutable
internal data class PlaylistEntry(val key: String, val index: Int, val track: UnifiedTrack)

internal fun playlistEntries(tracks: List<UnifiedTrack>): List<PlaylistEntry> {
    val seen = HashMap<String, Int>()
    return tracks.mapIndexed { index, track ->
        val copy = (seen[track.id] ?: 0) + 1
        seen[track.id] = copy
        PlaylistEntry(key = "${track.id}#$copy", index = index, track = track)
    }
}

/**
 * The rows whose title, artist or album contain [query], ignoring case; all of them for a blank
 * one. Each keeps its [PlaylistEntry.index], so a filtered row still plays and edits the right one.
 */
internal fun List<PlaylistEntry>.filteredBy(query: String): List<PlaylistEntry> {
    val term = query.trim()
    if (term.isEmpty()) return this
    return filter { (_, _, track) ->
        track.title.contains(term, ignoreCase = true) ||
            track.artist.contains(term, ignoreCase = true) ||
            track.album?.contains(term, ignoreCase = true) == true
    }
}

/** These rows with the one keyed [fromKey] taken to where [toKey] is; unchanged if either is gone. */
internal fun List<PlaylistEntry>.moving(fromKey: Any, toKey: Any): List<PlaylistEntry> {
    val from = indexOfFirst { it.key == fromKey }
    val to = indexOfFirst { it.key == toKey }
    if (from < 0 || to < 0) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
