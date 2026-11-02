package com.wander.android.core.playback.browse

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.wander.android.core.playback.toMediaItem
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns what another app asks to play into items the player can load.
 *
 * This app's own controller sends finished items — a placeholder URI the stream resolver
 * understands, built by [toMediaItem] — and they arrive with it intact because controller and
 * session share a process. A car, the Assistant or any other app in another process sends a
 * media id and nothing else: Media3 does not carry an item's URI across processes. Those ids are
 * looked up in Room here and built into the same items the app would have queued itself, so the
 * stream is resolved at play time exactly as it always is, and no URL ever passes through another
 * app.
 *
 * A single id picked from a folder of the browse tree plays that folder from it on — see
 * [BrowseId.Track] — except an in-progress episode, which plays alone from where it was left.
 * A voice request ("play … on Wanda", from a car or the Assistant) plays what [VoiceSearch]
 * finds for it.
 */
@UnstableApi
@Singleton
internal class PlayRequestResolver @Inject constructor(
    private val tracks: BrowseTracks,
    private val voiceSearch: VoiceSearch
) {

    /** Whether [items] are all ready to load as they stand, which is every request the app makes. */
    fun isReady(items: List<MediaItem>): Boolean = items.all { it.localConfiguration != null }

    /** [items] for adding to the queue, each looked up on its own. */
    suspend fun resolveEach(items: List<MediaItem>): List<MediaItem> = items.map { item ->
        if (item.localConfiguration != null) item else lookUp(item.mediaId).toMediaItem()
    }

    /** [items] as the whole new queue, expanded to its folder when it is one pick from the tree. */
    suspend fun resolveQueue(
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): MediaItemsWithStartPosition {
        val single = items.singleOrNull()?.takeIf { it.localConfiguration == null }
        // "Play … on Wanda": Media3 hands a voice request over as an item holding only the query.
        single?.requestMetadata?.searchQuery?.let { query ->
            return fromSearch(VoiceQuery.of(query, single.requestMetadata.extras))
        }
        val picked = single?.let { BrowseId.decode(it.mediaId) }
        return when (picked) {
            is BrowseId.Track -> fromFolder(picked, startPositionMs)
            is BrowseId.Node -> wholeFolder(picked)
            null -> MediaItemsWithStartPosition(resolveEach(items), startIndex, startPositionMs)
        }
    }

    private suspend fun fromFolder(picked: BrowseId.Track, startPositionMs: Long): MediaItemsWithStartPosition {
        val folder = if (picked.parent == BrowseId.Podcasts) null else tracks.of(picked.parent)
        val index = folder?.indexOfFirst { it.id == picked.trackId } ?: -1
        if (folder == null || index < 0) {
            // The folder changed since it was listed, or is one that does not play as a queue.
            val track = lookUp(picked.trackId)
            return MediaItemsWithStartPosition(listOf(track.toMediaItem()), 0, startAt(track, startPositionMs))
        }
        val from = (index - MAX_QUEUE / 2).coerceAtLeast(0)
        val queue = folder.subList(from, (from + MAX_QUEUE).coerceAtMost(folder.size))
        return MediaItemsWithStartPosition(queue.map(UnifiedTrack::toMediaItem), index - from, startPositionMs)
    }

    private suspend fun fromSearch(query: VoiceQuery): MediaItemsWithStartPosition {
        val found = voiceSearch.tracksFor(query)
        if (found.isEmpty()) throw NoSuchElementException("Nothing matched the voice search")
        return MediaItemsWithStartPosition(found.map(UnifiedTrack::toMediaItem), 0, C.TIME_UNSET)
    }

    private suspend fun wholeFolder(node: BrowseId.Node): MediaItemsWithStartPosition {
        val queue = tracks.of(node)?.take(MAX_QUEUE)
        if (queue.isNullOrEmpty()) throw NoSuchElementException("Nothing to play in the picked folder")
        return MediaItemsWithStartPosition(queue.map(UnifiedTrack::toMediaItem), 0, C.TIME_UNSET)
    }

    /** A tree id or a bare track id, as the Assistant and older browsers send. */
    private suspend fun lookUp(mediaId: String): UnifiedTrack {
        val trackId = (BrowseId.decode(mediaId) as? BrowseId.Track)?.trackId ?: mediaId
        return tracks.track(trackId) ?: throw NoSuchElementException("No such track to play")
    }

    /** An episode starts where it was left unless the request names a position of its own. */
    private suspend fun startAt(track: UnifiedTrack, requestedMs: Long): Long =
        if (requestedMs == C.TIME_UNSET || requestedMs == 0L) {
            tracks.resumePosition(track) ?: requestedMs
        } else {
            requestedMs
        }

    private companion object {
        /** Around the pick, so a liked list of thousands does not become a queue of thousands. */
        const val MAX_QUEUE = 500
    }
}
