package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.nameRes
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.CollapsingTitleState
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.components.rememberDownloadAll
import com.wander.android.ui.screens.album.AlbumHero

/** The playlist's cover, title and header actions: "Subsonic · 24 tracks · Shared · its description". */
@Composable
internal fun PlaylistHeader(
    playlist: UnifiedPlaylist?,
    tracks: List<UnifiedTrack>,
    shareActions: PlaylistShareActions,
    titleState: CollapsingTitleState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sharedKey: String? = null
) {
    val trackCount = playlist?.songCount?.takeIf { it > 0 } ?: tracks.size
    val subtitle = listOfNotNull(
        playlist?.source?.let { stringResource(it.nameRes) },
        pluralStringResource(R.plurals.collection_track_count, trackCount, trackCount),
        shareActions.sharedWith?.let { stringResource(R.string.playlist_shared_badge, stringResource(visibilityTitle(it))) },
        playlist?.comment?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    AlbumHero(
        title = playlist?.name ?: stringResource(R.string.playlist_fallback_title),
        subtitle = subtitle,
        artworkUrl = playlist?.coverArtUrl ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShare = shareActions.onShare,
        isShared = shareActions.sharedWith != null,
        onConvert = shareActions.onConvert,
        onExport = shareActions.onExport,
        onDownload = rememberDownloadAll(tracks),
        downloadLabel = R.string.playlist_download_all,
        modifier = Modifier.padding(bottom = 16.dp),
        titleModifier = Modifier.collapsingTitleSource(titleState),
        overline = R.string.hero_overline_playlist,
        sharedKey = sharedKey
    )
}
