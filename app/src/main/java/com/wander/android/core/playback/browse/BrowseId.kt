package com.wander.android.core.playback.browse

import java.net.URLDecoder
import java.net.URLEncoder

private const val PREFIX = "wanda"
private const val TRACK = "t"
private const val MIX = "mix"
private const val PLAYLIST = "playlist"
private const val ALBUM = "album"
private const val SEARCH = "search"
private const val SEPARATOR = "/"
private const val CHARSET = "UTF-8"

/** A fixed folder is the prefix and its name; a keyed one adds the row it names. */
private const val FIXED_SIZE = 2
private const val KEYED_SIZE = 3

/**
 * The media ids of the browse tree that cars and other media browsers walk.
 *
 * A folder's id names what it lists; a playable item's id names the track *and the folder it was
 * picked from*. The folder is what lets a tap in an album play the album from that track on, not
 * the one track alone: a car hands back only the id it was shown, and the queue is rebuilt from
 * it — see [PlayRequestResolver].
 *
 * Ids carry Room keys and nothing else. No stream URL, token or server address goes into one,
 * because every browser that is shown an id can read it.
 *
 * Encoded as `/`-joined segments, each URL-encoded, so an album or track id holding a `/` (a
 * podcast guid often is a URL) cannot be mistaken for a separator.
 */
internal sealed interface BrowseId {

    val segments: List<String>

    fun encode(): String = segments.joinToString(SEPARATOR) { URLEncoder.encode(it, CHARSET) }

    /** A folder in the tree. */
    sealed class Node(vararg parts: String) : BrowseId {
        override val segments: List<String> = listOf(PREFIX, *parts)
    }

    data object Root : Node("root")
    data object Home : Node("home")
    data object Library : Node("library")
    data object Recent : Node("recent")
    data object MostPlayed : Node("most")
    data object Liked : Node("liked")
    data object Playlists : Node("playlists")
    data object Albums : Node("albums")
    data object Downloads : Node("downloads")
    data object Podcasts : Node("podcasts")
    data class Mix(val seed: String) : Node(MIX, seed)
    data class Playlist(val playlistId: String) : Node(PLAYLIST, playlistId)
    data class Album(val albumId: String) : Node(ALBUM, albumId)
    data class Search(val query: String) : Node(SEARCH, query)

    /** [trackId], as listed under [parent]. */
    data class Track(val parent: Node, val trackId: String) : BrowseId {
        override val segments: List<String>
            get() = parent.segments + listOf(TRACK, trackId)
    }

    companion object {
        /** A `%` not followed by two hex digits, which [URLDecoder] refuses. */
        private val MALFORMED_ESCAPE = Regex("%(?![0-9A-Fa-f]{2})")

        private val FIXED: Map<String, Node> by lazy {
            listOf(Root, Home, Library, Recent, MostPlayed, Liked, Playlists, Albums, Downloads, Podcasts)
                .associateBy { it.segments[1] }
        }

        private val KEYED: Map<String, (String) -> Node> = mapOf(
            MIX to ::Mix,
            PLAYLIST to ::Playlist,
            ALBUM to ::Album,
            SEARCH to ::Search
        )

        /** The id [mediaId] encodes, or null when it is not one of this tree's ids. */
        fun decode(mediaId: String): BrowseId? {
            val parts = mediaId
                .takeIf { it.startsWith(PREFIX + SEPARATOR) && !MALFORMED_ESCAPE.containsMatchIn(it) }
                ?.split(SEPARATOR)
                ?.map { URLDecoder.decode(it, CHARSET) }
            return when {
                parts == null -> null
                // A track appends exactly two segments to its folder's.
                parts.size > KEYED_SIZE && parts[parts.size - 2] == TRACK ->
                    node(parts.dropLast(2))?.let { Track(it, parts.last()) }
                else -> node(parts)
            }
        }

        private fun node(parts: List<String>): Node? = when (parts.size) {
            FIXED_SIZE -> FIXED[parts[1]]
            KEYED_SIZE -> KEYED[parts[1]]?.invoke(parts[2])
            else -> null
        }
    }
}
