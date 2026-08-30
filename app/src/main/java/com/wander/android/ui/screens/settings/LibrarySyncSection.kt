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
    p2pEnabled: Boolean,
    onP2pEnabledChange: (Boolean) -> Unit,
    archiveEnabled: Boolean,
    canArchive: Boolean,
    onArchiveEnabledChange: (Boolean) -> Unit,
    pendingCount: Int,
    syncedCount: Int,
    progress: SyncProgress,
    serverSummary: String,
    onSyncNow: () -> Unit,
    onReviewDeletions: () -> Unit,
    canDelete: Boolean,
    /** Audio files stored on this device. Zero means there is nothing here to send. */
    localTrackCount: Int,
    /** The total number of tracks the server knows about, from any device or archive. */
    serverTotalTracks: Int,
    incognito: Boolean
) {
    item(key = "library_sync_section") { SettingsSection("Device & library sync") }

    item(key = "p2p_sync_toggle") {
        SettingsToggle(
            title = "P2P Device Sync",
            subtitle = if (incognito) {
                "Paused — incognito is on"
            } else {
                "Share songs between devices and index with server"
            },
            checked = p2pEnabled && !incognito,
            onCheckedChange = onP2pEnabledChange,
            enabled = !incognito
        )
    }

    item(key = "server_archive_toggle") {
        SettingsToggle(
            title = "Archive to server",
            subtitle = when {
                incognito -> "Paused — incognito is on"
                !canArchive -> "Your account is not allowed to upload files to this server."
                else -> "Upload full audio files to server storage."
            },
            checked = archiveEnabled && !incognito && canArchive,
            onCheckedChange = onArchiveEnabledChange,
            enabled = !incognito && canArchive
        )
    }

    if (canArchive) {
        item(key = "library_sync_delete") {
            SettingsRow(
                title = "Free up space on this device",
                subtitle = if (canDelete) {
                    "Delete the $syncedCount files your server already holds"
                } else {
                    "Nothing to remove — the server has confirmed no files yet"
                },
                onClick = onReviewDeletions,
                destructive = canDelete
            )
        }
    }

    if ((!p2pEnabled && !archiveEnabled) || incognito) return

    item(key = "library_sync_status") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = serverSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (localTrackCount == 0 && serverTotalTracks == 0) {
                Text(
                    text = "Nothing to send — no music files stored on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                val maxTracks = maxOf(serverTotalTracks, localTrackCount)
                val label = buildString {
                    append("$localTrackCount on this device")
                    if (serverTotalTracks > 0) append(" · $serverTotalTracks on server")
                    if (pendingCount > 0) append(" · $pendingCount pending sync")
                }
                
                LinearProgressIndicator(
                    progress = { if (maxTracks > 0) syncedCount.toFloat() / maxTracks else 0f },
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
            
            if (progress.running) {
                Text(
                    text = progress.currentTitle?.let { "Uploading $it" } ?: "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (progress.total > 0) {
                    LinearWavyProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
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
                progress.running -> "Running…"
                archiveEnabled -> "Send files to the server now"
                else -> "Update what your other devices can see. No files are sent."
            },
            onClick = onSyncNow
        )
    }
}
