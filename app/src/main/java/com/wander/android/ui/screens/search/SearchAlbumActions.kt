package com.wander.android.ui.screens.search

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.ui.components.AlbumActionsSheet
import com.wander.android.ui.components.rememberAlbumDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The long-press sheet of an album result: the same sheet, and the same actions, as an album
 * tile in the Library or on an artist's page.
 */
@Composable
internal fun SearchAlbumActions(
    album: UnifiedAlbum,
    onOpenArtist: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SearchAlbumActionsViewModel = hiltViewModel()
) {
    AlbumActionsSheet(
        album = album,
        onPlay = { viewModel.play(album) },
        onPlayNext = { viewModel.playNext(album) },
        onAddToQueue = { viewModel.addToQueue(album) },
        onDownload = rememberAlbumDownload(album),
        onGoToArtist = album.artist
            .takeIf { it.isNotBlank() }
            ?.let { artist -> { onOpenArtist(artist, album.artistId) } },
        // No `canShare` gate: an album link describes the record rather than naming a server.
        onShare = { viewModel.share(album) },
        onDismiss = onDismiss
    )
}

/** What [SearchAlbumActions] acts through; apart from [SearchViewModel], which is long enough. */
@HiltViewModel
internal class SearchAlbumActionsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val player: PlayerConnection
) : ViewModel() {

    fun play(album: UnifiedAlbum) = withTracks(album) { player.play(it) }

    fun playNext(album: UnifiedAlbum) = withTracks(album) { player.playNext(it) }

    fun addToQueue(album: UnifiedAlbum) = withTracks(album) { player.addToQueue(it) }

    fun share(album: UnifiedAlbum) = shareRepository.shareAlbum(album)

    /**
     * Runs [action] with [album]'s tracks. A failure is logged, not shown: the sheet is gone by
     * then, and the album screen is where a failed listing is shown — as in the Library's sheet.
     */
    private fun withTracks(album: UnifiedAlbum, action: (List<UnifiedTrack>) -> Unit) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
                .onFailure { Log.w(TAG, "Album tracks failed: ${it.loggableMessage()}") }
                .getOrNull()
                .orEmpty()
            if (tracks.isNotEmpty()) action(tracks)
        }
    }

    private companion object {
        const val TAG = "SearchAlbumActions"
    }
}
