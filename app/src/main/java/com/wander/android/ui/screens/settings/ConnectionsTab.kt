package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.wander.android.data.sources.agro.AgroSyncedSettings

/**
 * Where the music comes from: one row per backend, each saying whether it is connected and what
 * tapping it will do.
 */
internal fun LazyListScope.connectionsTab(
    navidromeConnected: Boolean,
    navidromeServer: String,
    syncedNavidrome: AgroSyncedSettings?,
    youTubeConnected: Boolean,
    localReady: Boolean,
    onNavidromeLogin: () -> Unit,
    onNavidromeSignOut: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onYouTubeSignOut: () -> Unit,
    onRescanLocal: () -> Unit
) {
    item(key = "navidrome") {
        SettingsRow(
            title = "Navidrome",
            // With settings sync on, an unconnected Navidrome still knows where it should point —
            // saying so is the difference between "sync did nothing" and "sync told this device
            // where to sign in".
            subtitle = when {
                navidromeConnected -> "Connected to $navidromeServer"
                syncedNavidrome?.serverUrl != null ->
                    "Agro has ${syncedNavidrome.serverUrl} — tap to sign in"
                else -> "Not connected"
            },
            onClick = if (navidromeConnected) onNavidromeSignOut else onNavidromeLogin
        )
    }

    item(key = "ytmusic") {
        SettingsRow(
            title = "YouTube Music",
            subtitle = if (youTubeConnected) {
                "Signed in — tap to sign out"
            } else {
                "Signed out. Search still works; your library needs sign-in."
            },
            onClick = if (youTubeConnected) onYouTubeSignOut else onYouTubeLogin
        )
    }

    // No Internet Archive row: it needs no account, so it had nothing to configure and no
    // onClick — a dead entry sitting among actionable ones.
    item(key = "local") {
        SettingsRow(
            title = "Music on this device",
            subtitle = if (localReady) {
                "Tap to rescan"
            } else {
                "Waiting for permission to read audio files"
            },
            onClick = onRescanLocal
        )
    }
}
