package com.wander.android.ui.screens.library

import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
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
    private val musicRepository: MusicRepository
) {
    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(scope: CoroutineScope, track: UnifiedTrack) {
        scope.launch { playbackCoordinator.startRadio(track) }
    }

    fun playFromLibrary(scope: CoroutineScope, track: UnifiedTrack, sourceFilter: SourceType?) {
        scope.launch {
            val ids = musicRepository.libraryTrackIds(sourceFilter)
            val index = ids.indexOf(track.id)
            if (index < 0) {
                playerConnection.play(listOf(track), 0)
            } else {
                val byId = musicRepository.tracksByIds(ids).associateBy { it.id }
                val ordered = ids.mapNotNull(byId::get)
                val position = ordered.indexOfFirst { it.id == ids[index] }.coerceAtLeast(0)
                playerConnection.play(ordered, position)
            }
        }
    }

    fun openPlaylist(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playPlaylistNext(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addPlaylistToQueue(scope: CoroutineScope, playlist: UnifiedPlaylist) {
        scope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addPlaylistToAnother(scope: CoroutineScope, playlist: UnifiedPlaylist, controller: AddToPlaylistController) {
        scope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) {
                controller.openForTracks(tracks, playlist.source)
            }
        }
    }

    fun playAlbum(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playAlbumNext(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addAlbumToQueue(scope: CoroutineScope, album: UnifiedAlbum) {
        scope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addAlbumToPlaylist(scope: CoroutineScope, album: UnifiedAlbum, controller: AddToPlaylistController) {
        scope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) controller.openForTracks(tracks, album.source)
        }
    }
}
