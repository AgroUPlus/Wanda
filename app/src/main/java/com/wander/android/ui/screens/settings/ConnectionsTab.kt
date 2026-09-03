package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

/**
 * Where the music comes from: one row per backend, each saying whether it is connected and what
 * tapping it will do.
 */
internal fun LazyListScope.connectionsTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "navidrome") {
        SettingsRow(
            title = "Navidrome",
            // With settings sync on, an unconnected Navidrome still knows where it should point —
            // saying so is the difference between "sync did nothing" and "sync told this device
            // where to sign in".
            subtitle = when {
                state.navidrome -> "Connected to ${state.navidromeServer}"
                state.syncedNavidrome?.serverUrl != null ->
                    "Agro has ${state.syncedNavidrome.serverUrl} — tap to sign in"
                else -> "Not connected"
            },
            onClick = if (state.navidrome) actions.onNavidromeSignOut else actions.onNavidromeLogin
        )
    }

    item(key = "ytmusic") {
        SettingsRow(
            title = "YouTube Music",
            subtitle = if (state.youTube) {
                // A Google account can hold several YouTube channels and only the session knows
                // which one is active, so naming it is the only way to be sure the right one is
                // signed in. Falls back to the bare state while the name is still unknown.
                state.youTubeAccount.takeIf { it.isNotBlank() }?.let { "Signed in as $it" } ?: "Signed in"
            } else {
                "Signed out. Search still works; your library needs sign-in."
            },
            onClick = if (state.youTube) actions.onYouTubeSignOut else actions.onYouTubeLogin
        )
    }

    // No Internet Archive row: it needs no account, so it had nothing to configure and no
    // onClick — a dead entry sitting among actionable ones.
    item(key = "local") {
        SettingsRow(
            title = "Music on this device",
            subtitle = when {
                !state.localReady -> "Waiting for permission to read audio files"
                state.localScanFolder != null -> "${state.localScanFolder} — tap to rescan, hold to change folder"
                actions.onPickLocalFolder != null ->
                    "Whole device — tap to rescan, hold to pick a folder"
                else -> "Tap to rescan"
            },
            onClick = actions.onRescanLocal,
            // A phone's audio is not all music: ringtones, podcast downloads and voice memos all
            // satisfy MediaStore's IS_MUSIC. Narrowing the scan is set once and forgotten, so it
            // sits behind a long press rather than taking a row from the action used every time.
            onLongClick = actions.onPickLocalFolder
        )
    }
}
