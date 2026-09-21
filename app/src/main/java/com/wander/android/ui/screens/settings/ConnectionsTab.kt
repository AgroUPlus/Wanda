package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.SourceIcon
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * Where the music comes from: one row per backend, each saying whether it is connected.
 */
internal fun LazyListScope.connectionsTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "sources") {
        GroupedCard(
            items = listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(0)),
                        title = stringResource(R.string.common_navidrome),
                        // With settings sync on, an unconnected Navidrome still knows where it
                        // should point — the user just needs telling it's ready, not how it got
                        // that way.
                        subtitle = when {
                            state.navidrome -> "Connected to ${state.navidromeServer}"
                            state.syncedNavidrome?.serverUrl != null -> "Ready to sign in"
                            else -> "Not connected"
                        },
                        onClick = if (state.navidrome) actions.onNavidromeSignOut else actions.onNavidromeLogin,
                        leading = { SourceIcon(SourceType.NAVIDROME) }
                    )
                },
                {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(1)),
                        title = stringResource(R.string.common_youtube_music),
                        subtitle = if (state.youTube) {
                            // A Google account can hold several YouTube channels and only the
                            // session knows which one is active, so naming it is the only way to
                            // be sure the right one is signed in. Falls back to the bare state.
                            state.youTubeAccount.takeIf { it.isNotBlank() }?.let { "Signed in as $it" } ?: "Signed in"
                        } else {
                            "Signed out"
                        },
                        onClick = if (state.youTube) actions.onYouTubeSignOut else actions.onYouTubeLogin,
                        leading = { SourceIcon(SourceType.YTMUSIC) }
                    )
                },
                {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(2)),
                        title = stringResource(R.string.common_music_device),
                        subtitle = when {
                            !state.localReady -> "Needs permission to read audio files"
                            state.localScanFolder != null -> state.localScanFolder
                            else -> "Whole device"
                        },
                        onClick = actions.onRescanLocal,
                        // A phone's audio is not all music: ringtones, podcast downloads and
                        // voice memos all satisfy MediaStore's IS_MUSIC. Narrowing the scan is
                        // set once and forgotten, so it sits behind a long press rather than
                        // taking a row from the everyday action.
                        onLongClick = actions.onPickLocalFolder,
                        leading = { SourceIcon(SourceType.LOCAL) }
                    )
                }
            )
        )
    }
}
