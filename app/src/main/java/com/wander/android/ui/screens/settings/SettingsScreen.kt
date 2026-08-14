package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navidrome by viewModel.navidromeConnected.collectAsStateWithLifecycle()
    val youTube by viewModel.youTubeConnected.collectAsStateWithLifecycle()
    val localReady by viewModel.localAvailable.collectAsStateWithLifecycle()
    val monet by viewModel.isMonetDynamic.collectAsStateWithLifecycle()
    val amoled by viewModel.isAmoledBlack.collectAsStateWithLifecycle()
    val offline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val incognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp)
            )
        }

        item(key = "sources") { SettingsSection("Sources") }
        item(key = "navidrome") {
            SettingsRow(
                title = "Navidrome",
                subtitle = if (navidrome) "Connected to ${viewModel.navidromeServer}"
                else "Not connected",
                onClick = if (navidrome) viewModel::disconnectNavidrome else onNavidromeLogin
            )
        }
        item(key = "ytmusic") {
            SettingsRow(
                title = "YouTube Music",
                subtitle = if (youTube) "Signed in — tap to sign out"
                else "Signed out. Search still works; your library needs sign-in.",
                onClick = if (youTube) viewModel::disconnectYouTube else onYouTubeLogin
            )
        }
        // No Internet Archive row: it needs no account, so it had nothing to configure and no
        // onClick — a dead entry sitting among actionable ones.
        item(key = "local") {
            SettingsRow(
                title = "Music on this device",
                subtitle = if (localReady) "Tap to rescan"
                else "Waiting for permission to read audio files",
                onClick = viewModel::rescanLocalLibrary
            )
        }

        item(key = "appearance") { SettingsSection("Appearance") }
        item(key = "monet") {
            SettingsToggle(
                title = "Match system colours",
                subtitle = "Use the wallpaper palette (Android 12+)",
                checked = monet,
                onCheckedChange = viewModel::setMonetDynamic
            )
        }
        item(key = "amoled") {
            SettingsToggle(
                title = "True black",
                subtitle = "Unlit pixels on OLED screens use no power",
                checked = amoled,
                onCheckedChange = viewModel::setAmoledBlack
            )
        }

        item(key = "playback") { SettingsSection("Playback and storage") }
        item(key = "offline") {
            SettingsToggle(
                title = "Offline mode",
                subtitle = "Only play what is already on this device",
                checked = offline,
                onCheckedChange = viewModel::setOfflineMode
            )
        }
        item(key = "download") {
            SettingsRow(
                title = "Download liked tracks now",
                subtitle = "Otherwise this happens on Wi-Fi while charging",
                onClick = viewModel::downloadLikedNow
            )
        }
        item(key = "cache") {
            SettingsRow(
                title = "Clear streaming cache",
                subtitle = formatBytes(cacheBytes) + " in use",
                onClick = viewModel::clearCache
            )
        }

        item(key = "privacy") { SettingsSection("Privacy") }
        item(key = "incognito") {
            SettingsToggle(
                title = "Incognito",
                subtitle = "Do not record play counts or scrobble to your server",
                checked = incognito,
                onCheckedChange = viewModel::setIncognito
            )
        }
        item(key = "divider") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item(key = "forget") {
            SettingsRow(
                title = "Forget all credentials",
                subtitle = "Signs out of every source and erases stored secrets",
                onClick = viewModel::forgetEverything,
                destructive = true
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "0 MB"
}
