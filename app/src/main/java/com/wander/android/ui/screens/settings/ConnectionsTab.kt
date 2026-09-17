package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.rememberShelfEntranceScale

private val Hue = SettingsCategory.CONNECTIONS.hue

/**
 * Where the music comes from: one row per backend, each saying whether it is connected and what
 * tapping it will do.
 */
internal fun LazyListScope.connectionsTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    var i = 0

    val navidromeIndex = i++
    item(key = "navidrome") {
        SettingsRow(
            modifier = Modifier.scale(rememberShelfEntranceScale(navidromeIndex)),
            title = stringResource(R.string.common_navidrome),
            // With settings sync on, an unconnected Navidrome still knows where it should point —
            // saying so is the difference between "sync did nothing" and "sync told this device
            // where to sign in".
            subtitle = when {
                state.navidrome -> "Connected to ${state.navidromeServer}"
                state.syncedNavidrome?.serverUrl != null ->
                    "Agro has ${state.syncedNavidrome.serverUrl} — tap to sign in"
                else -> "Not connected"
            },
            onClick = if (state.navidrome) actions.onNavidromeSignOut else actions.onNavidromeLogin,
            leading = { SourceBadge(SourceIdentity.NAVIDROME, Hue) }
        )
    }

    val ytMusicIndex = i++
    item(key = "ytmusic") {
        SettingsRow(
            modifier = Modifier.scale(rememberShelfEntranceScale(ytMusicIndex)),
            title = stringResource(R.string.common_youtube_music),
            subtitle = if (state.youTube) {
                // A Google account can hold several YouTube channels and only the session knows
                // which one is active, so naming it is the only way to be sure the right one is
                // signed in. Falls back to the bare state while the name is still unknown.
                state.youTubeAccount.takeIf { it.isNotBlank() }?.let { "Signed in as $it" } ?: "Signed in"
            } else {
                "Signed out. Search still works; your library needs sign-in."
            },
            onClick = if (state.youTube) actions.onYouTubeSignOut else actions.onYouTubeLogin,
            leading = { SourceBadge(SourceIdentity.YOUTUBE_MUSIC, Hue) }
        )
    }

    val localIndex = i++
    item(key = "local") {
        SettingsRow(
            modifier = Modifier.scale(rememberShelfEntranceScale(localIndex)),
            title = stringResource(R.string.common_music_device),
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
            onLongClick = actions.onPickLocalFolder,
            leading = { SourceBadge(SourceIdentity.LOCAL, Hue) }
        )
    }
}
