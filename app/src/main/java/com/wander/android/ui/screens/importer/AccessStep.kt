package com.wander.android.ui.screens.importer

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
 * Spotify, Deezer and Apple Music get a [GuideStep]: how to make the playlist public, then a link
 * to paste. YouTube Music already has an account elsewhere in this app, so its signed-in users
 * browse their library playlists instead and everyone else pastes a link.
 */
/** What the access step can ask of its host: loading playlists, signing in, and taking a pasted link. */
internal class AccessStepActions(
    val onSelectPlaylist: (RawUserPlaylistSummary) -> Unit,
    val onRefreshYouTube: () -> Unit,
    val onSwitchToDirectLink: () -> Unit,
    val onOpenYouTubeLogin: () -> Unit,
    val onInputChange: (String) -> Unit,
    val onLoadPlaylist: () -> Unit
)

@Composable
internal fun AccessStep(
    platform: PlatformType,
    state: PlaylistImportUiState,
    isYouTubeLoggedIn: Boolean,
    actions: AccessStepActions,
    modifier: Modifier = Modifier
) {
    val guide = platform.guideSteps()
    val isYouTube = platform == PlatformType.YOUTUBE
    val pasteLink = @Composable { linkModifier: Modifier ->
        ImportDirectLinkContent(
            manualInput = state.manualInput,
            isLoadingPlaylist = state.isLoadingPlaylist,
            error = state.error,
            onInputChange = actions.onInputChange,
            onLoadPlaylist = actions.onLoadPlaylist,
            modifier = linkModifier
        )
    }

    when {
        guide != null -> GuideStep(
            platform = platform,
            steps = guide,
            state = state,
            actions = actions,
            modifier = modifier
        )

        isYouTube && state.isDiscovering -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }

        isYouTube && state.discoveredPlaylists.isNotEmpty() -> DiscoveredPlaylistsGrid(
            platform = platform,
            playlists = state.discoveredPlaylists,
            onSelectPlaylist = actions.onSelectPlaylist,
            onRefresh = actions.onRefreshYouTube,
            onPasteLinkInstead = actions.onSwitchToDirectLink,
            modifier = modifier
        )

        // Distinct from the plain paste-link fallback below: this is what a completed, empty
        // discovery looks like, so it doesn't silently read as "the app never even tried."
        isYouTube && isYouTubeLoggedIn && state.hasCheckedDiscovery && !state.discoveryDismissed ->
            Column(modifier = modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.importer_no_playlists_found, platform.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(onClick = actions.onRefreshYouTube, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.importer_refresh_library))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    text = stringResource(R.string.importer_or_paste_link),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                pasteLink(Modifier.fillMaxWidth())
            }

        isYouTube && !isYouTubeLoggedIn -> Column(modifier = modifier.padding(16.dp)) {
            OwnAccountSignInPrompt(platform = platform, onOpenLogin = actions.onOpenYouTubeLogin)
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            Text(
                text = stringResource(R.string.importer_or_paste_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            pasteLink(Modifier.fillMaxWidth())
        }

        else -> Column(modifier = modifier.padding(16.dp)) {
            PlaylistFileButton(onText = actions.onInputChange)
            pasteLink(Modifier.fillMaxWidth())
        }
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
