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
 * YouTube Music and Deezer both already have an account elsewhere in this app; their signed-in
 * users browse discovered library playlists instead. For every other external platform (Spotify,
 * Apple Music), an embedded [ExternalPlatformWebView] catches web cookies while playlist importing
 * is done manually via share links.
 */
/** What the access step can ask of its host: loading playlists, signing in, and handing the web view over. */
internal class AccessStepActions(
    val onSelectPlaylist: (RawUserPlaylistSummary) -> Unit,
    val onRefreshYouTube: () -> Unit,
    val onRefreshDeezer: () -> Unit,
    val onSwitchToDirectLink: () -> Unit,
    val onOpenYouTubeLogin: () -> Unit,
    val onOpenDeezerLogin: () -> Unit,
    val onExternalWebViewReady: (WebView) -> Unit,
    val onWebUrlChanged: (String) -> Unit,
    val onInputChange: (String) -> Unit,
    val onLoadPlaylist: () -> Unit
)

@Composable
internal fun AccessStep(
    platform: PlatformType,
    state: PlaylistImportUiState,
    isYouTubeLoggedIn: Boolean,
    isDeezerLoggedIn: Boolean,
    actions: AccessStepActions,
    modifier: Modifier = Modifier
) {
    val hasOwnAccount = platform == PlatformType.YOUTUBE || platform == PlatformType.DEEZER
    val isLoggedIn = when (platform) {
        PlatformType.YOUTUBE -> isYouTubeLoggedIn
        PlatformType.DEEZER -> isDeezerLoggedIn
        else -> false
    }
    val onRefresh = if (platform == PlatformType.YOUTUBE) actions.onRefreshYouTube else actions.onRefreshDeezer
    val onOpenLogin = if (platform == PlatformType.YOUTUBE) actions.onOpenYouTubeLogin else actions.onOpenDeezerLogin

    when {
        hasOwnAccount && state.isDiscovering -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }

        hasOwnAccount && state.discoveredPlaylists.isNotEmpty() -> DiscoveredPlaylistsGrid(
            platform = platform,
            playlists = state.discoveredPlaylists,
            onSelectPlaylist = actions.onSelectPlaylist,
            onRefresh = onRefresh,
            onPasteLinkInstead = actions.onSwitchToDirectLink,
            modifier = modifier
        )

        // Distinct from the plain paste-link fallback below: this is what a completed, empty
        // discovery looks like, so it doesn't silently read as "the app never even tried."
        hasOwnAccount && isLoggedIn && state.hasCheckedDiscovery && !state.discoveryDismissed ->
            Column(modifier = modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.importer_no_playlists_found, platform.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(onClick = onRefresh, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.importer_refresh_library))
                }
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
                    onInputChange = actions.onInputChange,
                    onLoadPlaylist = actions.onLoadPlaylist,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        hasOwnAccount && !isLoggedIn -> Column(modifier = modifier.padding(16.dp)) {
            OwnAccountSignInPrompt(platform = platform, onOpenLogin = onOpenLogin)
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
                onInputChange = actions.onInputChange,
                onLoadPlaylist = actions.onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Deezer is already covered by the `hasOwnAccount` branches above; only Spotify and
        // Apple Music still fall through to the embedded-WebView + manual-paste flow.
        platform.webUrl != null && platform != PlatformType.DEEZER -> Column(modifier = modifier) {
            ExternalPlatformWebView(
                webUrl = platform.webUrl,
                onWebViewReady = actions.onExternalWebViewReady,
                onUrlChanged = actions.onWebUrlChanged,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            ImportDirectLinkContent(
                manualInput = state.manualInput,
                isLoadingPlaylist = state.isLoadingPlaylist,
                error = state.error,
                onInputChange = actions.onInputChange,
                onLoadPlaylist = actions.onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> ImportDirectLinkContent(
            manualInput = state.manualInput,
            isLoadingPlaylist = state.isLoadingPlaylist,
            error = state.error,
            onInputChange = actions.onInputChange,
            onLoadPlaylist = actions.onLoadPlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun OwnAccountSignInPrompt(platform: PlatformType, onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.importer_platform_signed_out_title, platform.displayName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.importer_youtube_signed_out_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Button(onClick = onOpenLogin, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.importer_sign_in))
            }
        }
    }
}
