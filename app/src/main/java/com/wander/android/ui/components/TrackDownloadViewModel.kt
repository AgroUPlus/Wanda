package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.DownloadMarks
import com.wander.android.data.repository.ManualDownloadRepository
import com.wander.android.data.repository.ManualDownloadRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Download and Remove download, for whatever shows a track: its actions sheet, a selection, an
 * album or playlist header.
 *
 * Resolved by each of those itself rather than handed down from every screen, for the reason
 * [TrackActionsSheet] resolves the jam: an action threaded through a dozen call sites is an action
 * missing from whichever one was forgotten.
 */
@HiltViewModel
class TrackDownloadViewModel @Inject constructor(
    private val downloads: ManualDownloadRepository
) : ViewModel() {

    val marks: StateFlow<DownloadMarks> = downloads.marks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadMarks())

    fun canDownload(track: UnifiedTrack): Boolean =
        ManualDownloadRules.canDownload(track) && !marks.value.isDownloaded(track) && !marks.value.isQueued(track)

    fun canRemove(track: UnifiedTrack): Boolean = ManualDownloadRules.canRemove(track, marks.value)

    fun download(tracks: List<UnifiedTrack>) {
        viewModelScope.launch { downloads.download(tracks.filter(::canDownload)) }
    }

    fun remove(tracks: List<UnifiedTrack>) {
        viewModelScope.launch { downloads.remove(tracks.filter(::canRemove)) }
    }
}

/**
 * The download state every [TrackRow] draws, provided once at the app's shell. An ambient for the
 * reason [LocalOfflinePlayback] is one: it is chrome on every list, owned by none of them.
 *
 * Empty by default, so a row outside the shell falls back to the track's own `isDownloaded`.
 */
val LocalDownloadMarks = compositionLocalOf { DownloadMarks() }

/**
 * "Download album" / "Download playlist": every one of [tracks] that can be downloaded and is not
 * on the device or on its way yet. Null once there are none, so the header drops the button
 * rather than offering one that would do nothing.
 */
@Composable
fun rememberDownloadAll(tracks: List<UnifiedTrack>): (() -> Unit)? {
    val downloads: TrackDownloadViewModel = hiltViewModel()
    val marks by downloads.marks.collectAsStateWithLifecycle()
    val wanted = remember(tracks, marks) { tracks.filter(downloads::canDownload) }
    return remember(wanted) { { downloads.download(wanted) }.takeIf { wanted.isNotEmpty() } }
}
