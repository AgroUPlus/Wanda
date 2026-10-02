package com.wander.android.ui.screens.playlist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.importer.M3uWriter
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.PlaylistFileExporter

/** What the playlist header offers; a null action is one this playlist does not have. */
internal class PlaylistShareActions(
    val onShare: (() -> Unit)?,
    val onConvert: (() -> Unit)?,
    val onDownload: (() -> Unit)?
)

/**
 * Hosts the playlist's share, visibility and convert sheets, the system save dialog for a
 * download, and the share sheet for a file, and returns the header actions that open them.
 */
@Composable
internal fun PlaylistShareHost(
    playlist: UnifiedPlaylist?,
    tracks: List<UnifiedTrack>,
    onOpenPlaylist: (String) -> Unit,
    viewModel: PlaylistShareViewModel = hiltViewModel()
): PlaylistShareActions {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val published by viewModel.publishedVisibility.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PlaylistFileExporter.MIME)
    ) { target -> if (target != null && playlist != null) viewModel.saveFile(target, playlist, tracks) }

    LaunchedEffect(viewModel, context) {
        viewModel.fileShares.collect { uri -> context.launchFileShare(uri) }
    }
    LaunchedEffect(viewModel) {
        viewModel.converted.collect(onOpenPlaylist)
    }

    if (playlist == null) return PlaylistShareActions(null, null, null)

    when (sheet) {
        PlaylistSheet.SHARE -> PlaylistShareSheet(
            originalSource = playlist.source.displayName.takeIf { viewModel.canShareOriginal(playlist) },
            canUseAgro = viewModel.canPublishToAgro,
            publishedVisibility = published,
            onOriginal = { viewModel.shareOriginal(playlist) },
            onLink = { viewModel.shareAsLink(playlist, tracks) },
            onFile = { viewModel.shareAsFile(playlist, tracks) },
            onAgro = { viewModel.open(PlaylistSheet.VISIBILITY) },
            onDismiss = viewModel::dismiss
        )
        PlaylistSheet.VISIBILITY -> PlaylistVisibilitySheet(
            current = published,
            onPick = { viewModel.pickVisibility(it, playlist, tracks) },
            onDismiss = viewModel::dismiss
        )
        PlaylistSheet.CONVERT -> ConvertPlaylistSheet(
            sourceName = playlist.source.displayName,
            onConvert = { viewModel.convert(playlist, tracks) },
            onDismiss = viewModel::dismiss
        )
        null -> Unit
    }

    val hasTracks = tracks.isNotEmpty()
    return PlaylistShareActions(
        onShare = { viewModel.open(PlaylistSheet.SHARE) }.takeIf { hasTracks },
        onConvert = { viewModel.open(PlaylistSheet.CONVERT) }.takeIf { viewModel.canConvert(playlist, tracks) },
        onDownload = { saveFile.launch(M3uWriter.fileName(playlist.name)) }.takeIf { hasTracks }
    )
}

/** Hands an exported file to whichever app the person picks, with a read grant on that URI only. */
private fun Context.launchFileShare(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PlaylistFileExporter.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // The chooser is the system's own activity, so it always resolves even when no app takes files.
    startActivity(Intent.createChooser(send, getString(R.string.playlist_share_title)))
}
