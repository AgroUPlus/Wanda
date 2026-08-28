package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.StorageUsage

/**
 * Library sync in Settings.
 *
 * Only shown once Agro is paired: without a server there is nowhere to sync to, and a toggle that
 * cannot do anything is worse than no toggle.
 */
internal fun LazyListScope.librarySyncSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    pendingCount: Int,
    syncedCount: Int,
    progress: SyncProgress,
    serverSummary: String,
    onSyncNow: () -> Unit,
    onReviewDeletions: () -> Unit,
    canDelete: Boolean,
    /** Audio files stored on this device. Zero means there is nothing here to send. */
    localTrackCount: Int,
    /** Null until the server has answered, or when it could not be asked. */
    storageUsage: StorageUsage?,
    incognito: Boolean
) {
    item(key = "library_sync_section") { SettingsSection("Library sync") }

    item(key = "library_sync_toggle") {
        SettingsToggle(
            title = "Send my music to Agro",
            subtitle = if (incognito) {
                "Paused — incognito is on"
            } else {
                "Local files only, on Wi-Fi while charging"
            },
            checked = enabled && !incognito,
            onCheckedChange = onEnabledChange,
            enabled = !incognito
        )
    }

    if (!enabled || incognito) return

    item(key = "library_sync_status") {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = serverSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (localTrackCount == 0) {
                    // The single most confusing state: everything is configured correctly, the
                    // counters read zero, and nothing explains why. This feature only ever sends
                    // files stored *on this device* — a library streamed from Navidrome has
                    // nothing for it to do. One line is enough to say so.
                    "Nothing to send — no music files stored on this device"
                } else {
                    "$syncedCount of $localTrackCount local tracks on the server · " +
                        "$pendingCount still to send"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (progress.running) {
                Text(
                    text = progress.currentTitle?.let { "Uploading $it" } ?: "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
                // Determinate only once a total is known; an indeterminate bar that later jumps to
                // a percentage reads as a restart.
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

    storageUsage?.let { usage ->
        item(key = "library_sync_quota") { StorageQuotaRow(usage) }
    }

    item(key = "library_sync_now") {
        SettingsRow(
            title = "Sync now",
            subtitle = if (progress.running) "Running…" else "Upload straight away",
            onClick = onSyncNow
        )
    }

    item(key = "library_sync_delete") {
        SettingsRow(
            title = "Free up space on this device",
            // The wording is the safety argument: only files the server has *confirmed* are
            // offered, so nothing is ever the last copy.
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

/**
 * The storage pool: how much of the account's allowance is gone.
 *
 * An uncapped account gets no bar at all rather than an empty or a full one. Both would be a
 * claim about a limit that does not exist — the admin owns the disk, and drawing a bar for them
 * invents a ceiling to worry about.
 */
@Composable
private fun StorageQuotaRow(usage: StorageUsage) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(text = "Storage pool", style = MaterialTheme.typography.titleMedium)

        val fraction = usage.fraction
        if (fraction != null) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                color = if (fraction >= NearlyFull) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp)
            )
        }

        Text(
            text = when {
                usage.quotaBytes == null ->
                    "${formatBytes(usage.usedBytes)} used · no limit on this account"
                else ->
                    "${formatBytes(usage.usedBytes)} of ${formatBytes(usage.quotaBytes)} used"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Where the bar turns red — late enough not to nag, early enough to still act on. */
private const val NearlyFull = 0.9f
