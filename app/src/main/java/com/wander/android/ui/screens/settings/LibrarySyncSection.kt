package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * Library sync in Settings.
 *
 * Only shown once Agro is paired: without a server there is nowhere to sync to, and a toggle that
 * cannot do anything is worse than no toggle.
 */
internal fun LazyListScope.librarySyncSection(
    state: SettingsUiState,
    actions: SettingsActions,
    /** One line describing where files end up, which depends on what else is connected. */
    @StringRes serverSummary: Int
) {
    item(key = "library_sync_section") {
        SettingsSection(
            stringResource(R.string.settings_section_device_library_sync),
            modifier = Modifier.scale(rememberShelfEntranceScale(0))
        )
    }

    item(key = "library_sync_toggles") {
        val rows = buildList<@Composable () -> Unit> {
            add {
                SettingsToggle(
                    title = stringResource(R.string.settings_p2p_device_sync),
                    subtitle = if (state.incognito) {
                        stringResource(R.string.settings_paused_incognito)
                    } else {
                        stringResource(R.string.settings_share_songs_between_devices_index_server)
                    },
                    checked = state.p2pSync && !state.incognito,
                    onCheckedChange = actions.onP2pSyncChange,
                    enabled = !state.incognito,
                    icon = Icons.Rounded.Devices
                )
            }
            add {
                SettingsToggle(
                    title = stringResource(R.string.settings_archive_server),
                    subtitle = when {
                        state.incognito -> stringResource(R.string.settings_paused_incognito)
                        !state.canArchive -> stringResource(R.string.settings_account_not_allowed_upload_files)
                        else -> stringResource(R.string.settings_upload_full_audio_files_server)
                    },
                    checked = state.serverArchive && !state.incognito && state.canArchive,
                    onCheckedChange = actions.onServerArchiveChange,
                    enabled = !state.incognito && state.canArchive,
                    icon = Icons.Rounded.Inventory2
                )
            }
            if (state.canArchive) {
                add {
                    SettingsRow(
                        title = stringResource(R.string.settings_free_up_space_device),
                        subtitle = if (state.canDelete) {
                            stringResource(R.string.settings_delete_files_server_already_holds, state.syncedTracks)
                        } else {
                            stringResource(R.string.settings_nothing_remove_server_confirmed_no_files)
                        },
                        onClick = actions.onReviewDeletions,
                        destructive = state.canDelete,
                        icon = Icons.Rounded.CleaningServices
                    )
                }
            }
        }
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(1)), items = rows)
    }

    if ((!state.p2pSync && !state.serverArchive) || state.incognito) return

    item(key = "library_sync_status") { SyncStatus(state, serverSummary) }

    item(key = "library_sync_now") {
        GroupedCard(
            modifier = Modifier.scale(rememberShelfEntranceScale(2)),
            items = listOf<@Composable () -> Unit>({
                SettingsRow(
                    title = stringResource(R.string.settings_sync_now),
                    subtitle = when {
                        state.syncProgress.running -> stringResource(R.string.settings_running)
                        state.serverArchive -> stringResource(R.string.settings_send_files_to_server_now)
                        else -> stringResource(R.string.settings_update_other_devices_no_files_sent)
                    },
                    onClick = actions.onSyncNow,
                    icon = Icons.Rounded.Sync
                )
            })
        )
    }
}

@Composable
private fun SyncStatus(state: SettingsUiState, @StringRes serverSummary: Int) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(serverSummary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.localTracks == 0 && state.serverTotalTracks == 0) {
            Text(
                text = stringResource(R.string.settings_nothing_send_no_music_files),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            val maxTracks = maxOf(state.serverTotalTracks, state.localTracks)
            val label = listOfNotNull(
                stringResource(R.string.settings_sync_count_device, state.localTracks),
                state.serverTotalTracks.takeIf { it > 0 }?.let { stringResource(R.string.settings_sync_count_server, it) },
                state.pendingUploads.takeIf { it > 0 }?.let { stringResource(R.string.settings_sync_count_pending, it) }
            ).joinToString(" · ")

            LinearWavyProgressIndicator(
                progress = { if (maxTracks > 0) state.syncedTracks.toFloat() / maxTracks else 0f },
                // The wave is whether the upload is actually moving, the same as it means on
                // the fingerprints screen. A determinate bar sitting at 60% looks identical
                // whether a sync is running or stopped hours ago, and that is precisely the
                // question this section is opened to answer.
                amplitude = { if (state.syncProgress.running) 1f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (state.syncProgress.running) {
            Text(
                text = state.syncProgress.currentTitle?.let { stringResource(R.string.settings_sync_uploading, it) }
                    ?: stringResource(R.string.settings_sync_working),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (state.syncProgress.total > 0) {
                LinearWavyProgressIndicator(
                    progress = { state.syncProgress.done.toFloat() / state.syncProgress.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            } else {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }
    }
}
