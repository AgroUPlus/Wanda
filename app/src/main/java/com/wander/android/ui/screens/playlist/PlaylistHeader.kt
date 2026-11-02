package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.CollapsingTitleState
import com.wander.android.ui.components.collapsingTitleSource
import com.wander.android.ui.screens.album.AlbumHero

/** The playlist's cover, title and header actions: "Navidrome · 24 tracks · Shared · its description". */
@Composable
internal fun PlaylistHeader(
    playlist: UnifiedPlaylist?,
    tracks: List<UnifiedTrack>,
    shareActions: PlaylistShareActions,
    titleState: CollapsingTitleState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    val trackCount = playlist?.songCount?.takeIf { it > 0 } ?: tracks.size
    val subtitle = listOfNotNull(
        playlist?.source?.displayName,
        "$trackCount track${if (trackCount == 1) "" else "s"}",
        shareActions.sharedWith?.let { stringResource(R.string.playlist_shared_badge, stringResource(visibilityTitle(it))) },
        playlist?.comment?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    AlbumHero(
        title = playlist?.name ?: "Playlist",
        subtitle = subtitle,
        artworkUrl = playlist?.coverArtUrl ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShare = shareActions.onShare,
        isShared = shareActions.sharedWith != null,
        onConvert = shareActions.onConvert,
        onDownload = shareActions.onDownload,
        modifier = Modifier.padding(bottom = 16.dp),
        titleModifier = Modifier.collapsingTitleSource(titleState),
        overline = R.string.hero_overline_playlist
    )
}
