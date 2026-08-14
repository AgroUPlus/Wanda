package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.permissions.rememberPermissionGate

/**
 * First-run setup. Every source is optional — music on the device works with nothing configured —
 * so this can be skipped and reached again later from Settings.
 */
@Composable
fun WelcomeScreen(
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onDone: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    // Prompted from the card's button rather than on entry, so the reason is on screen first.
    val permissionGate = rememberPermissionGate(
        onAudioGranted = viewModel::refreshLocal,
        requestOnStart = false
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(text = "Welcome to Wanda", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "One library, one queue, one player — across your own server, this device, " +
                "YouTube Music and the Internet Archive. No accounts, no telemetry.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        SourceSetupCard(
            title = "Music on this device",
            description = "Plays offline, costs nothing, needs no account.",
            icon = Icons.Rounded.MusicNote,
            isConfigured = status.localGranted,
            actionLabel = "Grant access",
            onAction = permissionGate::request
        )

        SourceSetupCard(
            title = "Navidrome",
            description = "Your own Subsonic-compatible server, at full quality.",
            icon = Icons.Rounded.Cloud,
            isConfigured = status.navidromeConfigured,
            actionLabel = "Sign in",
            onAction = onNavidromeLogin
        )

        SourceSetupCard(
            title = "YouTube Music",
            description = "Signs in through your own session; the cookie never leaves the device.",
            icon = Icons.Rounded.LibraryMusic,
            isConfigured = status.ytMusicConfigured,
            actionLabel = "Sign in",
            onAction = onYouTubeLogin
        )

        SourceSetupCard(
            title = "Internet Archive",
            description = "Live sets and public-domain recordings. Nothing to set up.",
            icon = Icons.Rounded.Public,
            isConfigured = true,
            actionLabel = "",
            onAction = {}
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.finish()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start listening")
        }

        TextButton(
            onClick = {
                viewModel.finish()
                onDone()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Set this up later")
        }
    }
}
