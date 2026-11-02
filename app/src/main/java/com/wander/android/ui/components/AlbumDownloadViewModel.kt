package com.wander.android.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.loggableMessage
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.repository.ManualDownloadRepository
import com.wander.android.data.repository.ManualDownloadRules
import com.wander.android.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Download" on an album's long-press sheet, where the album's tracks are not on screen yet: they
 * are fetched, and every one that can be downloaded and is not on the device or on its way is
 * handed to [ManualDownloadRepository], as the album screen's header does with the tracks it shows.
 *
 * Resolved by each sheet's caller through [rememberAlbumDownload] rather than by every screen's
 * view model, for the reason [TrackDownloadViewModel] gives.
 */
@HiltViewModel
class AlbumDownloadViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloads: ManualDownloadRepository
) : ViewModel() {

    fun download(album: UnifiedAlbum) {
        viewModelScope.launch {
            // Logged, not shown: a long-press sheet has no screen to report on, and the album
            // screen is where a failed track listing is shown — as the sheet's other actions do.
            val tracks = musicRepository.getAlbumTracks(album)
                .onFailure { Log.w(TAG, "Album tracks failed: ${it.loggableMessage()}") }
                .getOrNull() ?: return@launch
            val marks = downloads.marks.first()
            downloads.download(
                tracks.filter {
                    ManualDownloadRules.canDownload(it) && !marks.isDownloaded(it) && !marks.isQueued(it)
                }
            )
        }
    }

    private companion object {
        const val TAG = "AlbumDownload"
    }
}

/**
 * The album sheet's Download action, or null for an album none of whose tracks can be downloaded
 * — a local record is already on the device — so the sheet drops the button.
 */
@Composable
fun rememberAlbumDownload(album: UnifiedAlbum): (() -> Unit)? {
    val viewModel: AlbumDownloadViewModel = hiltViewModel()
    return remember(album, viewModel) {
        { viewModel.download(album) }.takeIf { ManualDownloadRules.canDownload(album) }
    }
}
