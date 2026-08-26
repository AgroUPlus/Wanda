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
    onRescanLocal: () -> Unit,
    /** Null when this device is too old to narrow the scan — see `supportsFolderScan`. */
    onPickLocalFolder: (() -> Unit)?,
    /** The chosen folder, or null for the whole device. */
    localFolder: String?
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
                "Signed in"
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
            subtitle = when {
                !localReady -> "Waiting for permission to read audio files"
                localFolder != null -> "$localFolder — tap to rescan, hold to change folder"
                onPickLocalFolder != null ->
                    "Whole device — tap to rescan, hold to pick a folder"
                else -> "Tap to rescan"
            },
            onClick = onRescanLocal,
            // A phone's audio is not all music: ringtones, podcast downloads and voice memos all
            // satisfy MediaStore's IS_MUSIC. Narrowing the scan is set once and forgotten, so it
            // sits behind a long press rather than taking a row from the action used every time.
            onLongClick = onPickLocalFolder
        )
    }
}
