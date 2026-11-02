package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack

/** [TrackActionsSheet]'s Download and Remove download; null where the track offers neither. */
internal class TrackSheetDownloads(
    val download: (() -> Unit)?,
    val remove: (() -> Unit)?,
    /** Whether [remove] asks first, and so closes the sheet with its own dialog. */
    val removeAsks: Boolean
)

/**
 * Resolved from [TrackDownloadViewModel] by the sheet itself, like its jam and drop actions, so
 * every screen that shows the sheet offers them. Removing a file asks first through [onConfirm];
 * taking back a download still waiting has nothing to lose and does not.
 */
@Composable
internal fun rememberTrackSheetDownloads(
    track: UnifiedTrack,
    onConfirm: (ConfirmRequest) -> Unit
): TrackSheetDownloads {
    val downloads: TrackDownloadViewModel = hiltViewModel()
    val marks by downloads.marks.collectAsStateWithLifecycle()
    val title = stringResource(R.string.confirm_delete_download_title)
    val message = stringResource(R.string.confirm_delete_download_message, track.title)
    val confirmLabel = stringResource(R.string.common_delete_offline_file)
    val removeAsks = marks.isDownloaded(track)
    val remove = { downloads.remove(listOf(track)) }
    return TrackSheetDownloads(
        download = { downloads.download(listOf(track)) }.takeIf { downloads.canDownload(track, marks) },
        remove = when {
            !downloads.canRemove(track, marks) -> null
            removeAsks -> { { onConfirm(ConfirmRequest(title, message, confirmLabel, remove)) } }
            else -> remove
        },
        removeAsks = removeAsks
    )
}
