package com.wander.android.ui.screens.artist

import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates playback, queuing, liking, and sharing actions for an artist's tracks and albums.
 */
internal class ArtistPlaybackCoordinator @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val playbackCoordinator: PlaybackCoordinator,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository
) {
    fun playTop(topSongs: List<UnifiedTrack>) =
        topSongs.takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle(topSongs: List<UnifiedTrack>) =
        topSongs.takeIf { it.isNotEmpty() }?.let { playerConnection.play(it.shuffled()) }

    fun play(topSongs: List<UnifiedTrack>, index: Int) = playerConnection.play(topSongs, index)

    fun playOne(track: UnifiedTrack) = playerConnection.play(listOf(track))

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(track: UnifiedTrack, scope: CoroutineScope) {
        scope.launch { playbackCoordinator.startRadio(track) }
    }

    fun toggleLike(track: UnifiedTrack, scope: CoroutineScope) {
        scope.launch { musicRepository.toggleLike(track) }
    }

    fun canShare(track: UnifiedTrack): Boolean = shareRepository.canShare(track)

    fun share(track: UnifiedTrack, scope: CoroutineScope) {
        scope.launch { shareRepository.share(track) }
    }

    fun playAlbum(album: UnifiedAlbum, scope: CoroutineScope) {
        scope.launch {
            val albumTracks = musicRepository.getAlbumTracks(album)
            if (albumTracks.isNotEmpty()) {
                playerConnection.play(albumTracks)
            }
        }
    }

    fun playAlbumNext(album: UnifiedAlbum, scope: CoroutineScope) {
        scope.launch {
            val albumTracks = musicRepository.getAlbumTracks(album)
            if (albumTracks.isNotEmpty()) {
                playerConnection.playNext(albumTracks)
            }
        }
    }

    fun addAlbumToQueue(album: UnifiedAlbum, scope: CoroutineScope) {
        scope.launch {
            val albumTracks = musicRepository.getAlbumTracks(album)
            if (albumTracks.isNotEmpty()) {
                playerConnection.addToQueue(albumTracks)
            }
        }
    }

    fun canShareAlbum(album: UnifiedAlbum): Boolean =
        shareRepository.canShare(album.source)

    fun shareAlbum(album: UnifiedAlbum, scope: CoroutineScope) {
        scope.launch {
            shareRepository.share(
                ShareTarget(
                    kind = ShareKind.ALBUM,
                    source = album.source,
                    id = album.id,
                    title = "${album.title} - ${album.artist}"
                )
            )
        }
    }

    fun getAlbumTracks(
        album: UnifiedAlbum,
        scope: CoroutineScope,
        onTracks: (List<UnifiedTrack>) -> Unit
    ) {
        scope.launch {
            val albumTracks = musicRepository.getAlbumTracks(album)
            if (albumTracks.isNotEmpty()) {
                onTracks(albumTracks)
            }
        }
    }
}
