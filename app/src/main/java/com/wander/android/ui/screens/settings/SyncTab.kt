package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.wander.android.data.repository.SyncProgress
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode

/**
 * Everything Agro: the pairing itself, then the two things a pairing buys — settings shared between
 * devices, and music shared between them.
 *
 * Nothing below the pairing row exists until there is a server to talk to. A toggle that cannot do
 * anything is worse than no toggle.
 */
internal fun LazyListScope.syncTab(
    paired: Boolean,
    devicePetname: String,
    server: String,
    syncSettings: Boolean,
    onSyncSettingsChange: (Boolean) -> Unit,
    onPair: () -> Unit,
    onUnpair: () -> Unit,
    devices: List<AgroNode>,
    handoff: AgroHandoffState?,
    isResuming: Boolean,
    onResume: (AgroHandoffState) -> Unit,
    librarySyncEnabled: Boolean,
    onLibrarySyncChange: (Boolean) -> Unit,
    pendingUploads: Int,
    syncedTracks: Int,
    localTracks: Int,
    syncProgress: SyncProgress,
    filesLandInNavidrome: Boolean,
    onSyncNow: () -> Unit,
    onReviewDeletions: () -> Unit,
    canDelete: Boolean
) {
    item(key = "agro") {
        SettingsRow(
            title = "Agro Device",
            subtitle = if (paired) {
                "$devicePetname • $server • Tap to unpair"
            } else {
                "Not paired — scan the pairing QR, or tap to enter a server"
            },
            onClick = if (paired) onUnpair else onPair
        )
    }

    if (!paired) return

    item(key = "agro_sync") {
        SettingsToggle(
            title = "Sync settings with Agro",
            subtitle = "Share the Navidrome address between devices. Passwords never leave here.",
            checked = syncSettings,
            onCheckedChange = onSyncSettingsChange
        )
    }

    agroDevicesSection(
        devices = devices,
        handoff = handoff,
        isResuming = isResuming,
        onResume = onResume
    )

    librarySyncSection(
        enabled = librarySyncEnabled,
        onEnabledChange = onLibrarySyncChange,
        pendingCount = pendingUploads,
        syncedCount = syncedTracks,
        progress = syncProgress,
        serverSummary = if (filesLandInNavidrome) {
            "Filed into your Navidrome library."
        } else {
            "Held on your Agro server for your other devices."
        },
        onSyncNow = onSyncNow,
        onReviewDeletions = onReviewDeletions,
        canDelete = canDelete,
        localTrackCount = localTracks
    )
}
