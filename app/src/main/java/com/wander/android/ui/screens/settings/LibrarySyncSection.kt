package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.SyncProgress
import kotlin.math.max

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
    serverSummary: String
) {
    item(key = "library_sync_section") { SettingsSection("Device & library sync") }

    item(key = "p2p_sync_toggle") {
        SettingsToggle(
            title = "P2P Device Sync",
            subtitle = if (state.incognito) {
                "Paused — incognito is on"
            } else {
                "Share songs between devices and index with server"
            },
            checked = state.p2pSync && !state.incognito,
            onCheckedChange = actions.onP2pSyncChange,
            enabled = !state.incognito
        )
    }

    item(key = "server_archive_toggle") {
        SettingsToggle(
            title = "Archive to server",
            subtitle = when {
                state.incognito -> "Paused — incognito is on"
                !state.canArchive -> "Your account is not allowed to upload files to this server."
                else -> "Upload full audio files to server storage."
            },
            checked = state.serverArchive && !state.incognito && state.canArchive,
            onCheckedChange = actions.onServerArchiveChange,
            enabled = !state.incognito && state.canArchive
        )
    }

    if (state.canArchive) {
        item(key = "library_sync_delete") {
            SettingsRow(
                title = "Free up space on this device",
                subtitle = if (state.canDelete) {
                    "Delete the ${state.syncedTracks} files your server already holds"
                } else {
                    "Nothing to remove — the server has confirmed no files yet"
                },
                onClick = actions.onReviewDeletions,
                destructive = state.canDelete
            )
        }
    }

    if ((!state.p2pSync && !state.serverArchive) || state.incognito) return

    item(key = "library_sync_status") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = serverSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (state.localTracks == 0 && state.serverTotalTracks == 0) {
                Text(
                    text = "Nothing to send — no music files stored on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                val maxTracks = maxOf(state.serverTotalTracks, state.localTracks)
                val label = buildString {
                    append("${state.localTracks} on this device")
                    if (state.serverTotalTracks > 0) append(" · ${state.serverTotalTracks} on server")
                    if (state.pendingUploads > 0) append(" · ${state.pendingUploads} pending sync")
                }
                
                LinearProgressIndicator(
                    progress = { if (maxTracks > 0) state.syncedTracks.toFloat() / maxTracks else 0f },
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
                    text = state.syncProgress.currentTitle?.let { "Uploading $it" } ?: "Working…",
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

    item(key = "library_sync_now") {
        SettingsRow(
            title = "Sync now",
            subtitle = when {
                state.syncProgress.running -> "Running…"
                state.serverArchive -> "Send files to the server now"
                else -> "Update what your other devices can see. No files are sent."
            },
            onClick = actions.onSyncNow
        )
    }
}
