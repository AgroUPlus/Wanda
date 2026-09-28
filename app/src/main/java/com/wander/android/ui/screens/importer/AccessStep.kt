package com.wander.android.ui.screens.importer

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawUserPlaylistSummary

/**
 * Step 2: how to get a playlist, once a platform is chosen.
 *
 * For YouTube Music, signed-in users can browse discovered library playlists or paste a link.
 * For external platforms (Spotify, Deezer, Apple Music), an embedded [ExternalPlatformWebView]
 * catches web cookies while playlist importing is done manually via share links.
 */
@Composable
internal fun AccessStep(
    platform: PlatformType,
    state: PlaylistImportUiState,
    isYouTubeLoggedIn: Boolean,
    onSelectPlaylist: (RawUserPlaylistSummary) -> Unit,
    onRefreshYouTube: () -> Unit,
    onSwitchToDirectLink: () -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onExternalWebViewReady: (WebView) -> Unit,
    onWebUrlChanged: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onLoadPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        platform == PlatformType.YOUTUBE && state.isDiscovering -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }

        platform == PlatformType.YOUTUBE && state.discoveredPlaylists.isNotEmpty() -> DiscoveredPlaylistsGrid(
            platform = platform,
            playlists = state.discoveredPlaylists,
            onSelectPlaylist = onSelectPlaylist,
            onRefresh = onRefreshYouTube,
            onPasteLinkInstead = onSwitchToDirectLink,
            modifier = modifier
        )

        platform == PlatformType.YOUTUBE && !isYouTubeLoggedIn -> Column(modifier = modifier.padding(16.dp)) {
            YouTubeSignInPrompt(onOpenYouTubeLogin = onOpenYouTubeLogin)
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            Text(
                text = stringResource(R.string.importer_or_paste_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ImportDirectLinkContent(
                manualInput = state.manualInput,
                isLoadingPlaylist = state.isLoadingPlaylist,
                error = state.error,
                onInputChange = onInputChange,
                onLoadPlaylist = onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        platform.webUrl != null -> Column(modifier = modifier) {
            ExternalPlatformWebView(
                webUrl = platform.webUrl,
                onWebViewReady = onExternalWebViewReady,
                onUrlChanged = onWebUrlChanged,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            ImportDirectLinkContent(
                manualInput = state.manualInput,
                isLoadingPlaylist = state.isLoadingPlaylist,
                error = state.error,
                onInputChange = onInputChange,
                onLoadPlaylist = onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> ImportDirectLinkContent(
            manualInput = state.manualInput,
            isLoadingPlaylist = state.isLoadingPlaylist,
            error = state.error,
            onInputChange = onInputChange,
            onLoadPlaylist = onLoadPlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun YouTubeSignInPrompt(onOpenYouTubeLogin: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.importer_youtube_signed_out_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.importer_youtube_signed_out_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Button(onClick = onOpenYouTubeLogin, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.importer_sign_in))
            }
        }
    }
}
