package com.wander.android.ui.screens.library

import android.util.Log
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.ui.components.AddToPlaylistController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates playback, queuing, and playlist insertion operations initiated from the Library screen.
 */
@Singleton
class LibraryPlaybackCoordinator @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val playbackCoordinator: PlaybackCoordinator,
    private val musicRepository: MusicRepository,
    private val browse: LibraryBrowseRepository
) {
    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(scope: CoroutineScope, track: UnifiedTrack) {
        scope.launch { playbackCoordinator.startRadio(track) }
    }

    /**
     * Plays [track] inside the Tracks tab as it is shown, as a window of at most [QUEUE_CAP] rows
     * around it rather than the whole library: a ten-thousand-row queue is ten thousand tracks
     * mapped in memory and handed to the player, for a listener who will hear a few dozen.
     *
     * Only the ids are read in full, to find where the track sits.
     */
    fun playFromLibrary(scope: CoroutineScope, track: UnifiedTrack, sort: LibrarySort, sources: Set<SourceType>) {
        scope.launch {
            val index = browse.trackIds(sort, sources).indexOf(track.id)
            if (index < 0) {
                playerConnection.play(listOf(track), 0)
                return@launch
            }
            val start = (index - QUEUE_LEAD).coerceAtLeast(0)
            val window = browse.tracksSlice(sort, sources, offset = start, limit = QUEUE_CAP)
            val position = window.indexOfFirst { it.id == track.id }
            // The library changed between the two reads; play what was tapped rather than a guess.
            if (position < 0) playerConnection.play(listOf(track), 0) else playerConnection.play(window, position)
        }
    }

    /** Play all, or Shuffle all, for the Tracks tab: its first, or a random, [QUEUE_CAP] rows. */
    fun playLibrary(scope: CoroutineScope, sort: LibrarySort, sources: Set<SourceType>, shuffled: Boolean) {
        scope.launch {
            val tracks = if (shuffled) {
                browse.randomTracks(sources, QUEUE_CAP)
            } else {
                browse.tracksSlice(sort, sources, offset = 0, limit = QUEUE_CAP)
            }
            if (tracks.isNotEmpty()) playerConnection.play(tracks, 0)
        }
    }

    fun openPlaylist(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = playlistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playPlaylistNext(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = playlistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addPlaylistToQueue(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = playlistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addPlaylistToAnother(scope: CoroutineScope, playlist: UnifiedPlaylist, controller: AddToPlaylistController) {
        scope.launch {
            val tracks = playlistTracks(playlist)
            if (tracks.isNotEmpty()) {
                controller.openForTracks(tracks, playlist.source)
            }
        }
    }

    fun playAlbum(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = albumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playAlbumNext(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = albumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addAlbumToQueue(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = albumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addAlbumToPlaylist(scope: CoroutineScope, album: UnifiedAlbum, controller: AddToPlaylistController) {
        scope.launch {
            val tracks = albumTracks(album)
            if (tracks.isNotEmpty()) controller.openForTracks(tracks, album.source)
        }
    }

    /**
     * The tracks behind a sheet action, or empty when they could not be had. These actions have no
     * screen of their own to report on; the failure is logged, and the album or playlist screen is
     * where it is shown.
     */
    private suspend fun albumTracks(album: UnifiedAlbum): List<UnifiedTrack> =
        musicRepository.getAlbumTracks(album)
            .onFailure { Log.w(TAG, "Album tracks failed: ${it.loggableMessage()}") }
            .getOrNull().orEmpty()

    private suspend fun playlistTracks(playlist: UnifiedPlaylist): List<UnifiedTrack> =
        musicRepository.getPlaylistTracks(playlist)
            .onFailure { Log.w(TAG, "Playlist tracks failed: ${it.loggableMessage()}") }
            .getOrNull().orEmpty()

    private companion object {
        const val TAG = "LibraryPlayback"

        /** The most a library play queues at once. */
        const val QUEUE_CAP = 500

        /** How many of the rows before a tapped track stay in its queue, so Previous still works. */
        const val QUEUE_LEAD = 50
    }
}
