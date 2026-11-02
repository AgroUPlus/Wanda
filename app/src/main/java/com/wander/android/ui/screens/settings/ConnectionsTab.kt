package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.core.permissions.rememberPermissionGate
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
                        title = stringResource(R.string.deezer_account),
                        subtitle = if (state.deezer) {
                            val account = state.deezerAccount.takeIf { it.isNotBlank() } ?: "Signed in"
                            val tierDisplay = when (state.deezerTier) {
                                "HIFI" -> stringResource(R.string.deezer_tier_hifi)
                                "PREMIUM" -> stringResource(R.string.deezer_tier_premium)
                                else -> stringResource(R.string.deezer_tier_free)
                            }
                            "$account ($tierDisplay)"
                        } else {
                            "Signed out"
                        },
                        onClick = if (state.deezer) actions.onDeezerSignOut else actions.onDeezerLogin,
                        leading = { SourceIcon(SourceType.DEEZER) }
                    )
                },
                {
                    // Audio only: the row says it is about music files, so a notification prompt
                    // riding along would be unexplained. The scan that follows a grant is started
                    // by the app-wide gate, which notices the grant when the activity resumes.
                    val audioGate = rememberPermissionGate(requestOnStart = false, askNotifications = false)
                    // localReady lags a fresh grant until that scan runs; the gate does not.
                    val needsAccess = !state.localReady && !audioGate.hasAudio
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(3)),
                        title = stringResource(R.string.common_music_device),
                        subtitle = when {
                            needsAccess && audioGate.audioBlocked -> stringResource(R.string.settings_local_access_blocked)
                            needsAccess -> stringResource(R.string.settings_local_access_needed)
                            state.localScanFolder != null -> state.localScanFolder
                            else -> stringResource(R.string.settings_local_whole_device)
                        },
                        // Without access a rescan finds nothing; the tap goes where access is given.
                        onClick = if (needsAccess) audioGate::request else actions.onRescanLocal,
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

    if (state.navidrome || state.deezer) {
        item(key = "sec_stream_quality") {
            SettingsSection(stringResource(R.string.settings_section_stream_quality))
        }
        item(key = "stream_quality") {
            val scale = Modifier.scale(rememberShelfEntranceScale(4))
            GroupedCard(
                items = buildList<@Composable () -> Unit> {
                    if (state.navidrome) {
                        add { NavidromeQualityRow(metered = false, modifier = scale) }
                        add { NavidromeQualityRow(metered = true, modifier = scale) }
                    }
                    if (state.deezer) add { DeezerQualityRow(modifier = scale) }
                }
            )
        }
    }
}
