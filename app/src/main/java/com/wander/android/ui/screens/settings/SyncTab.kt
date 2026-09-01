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
    connection: AgroConnectionState,
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
    p2pSyncEnabled: Boolean,
    onP2pSyncChange: (Boolean) -> Unit,
    serverArchiveEnabled: Boolean,
    canArchive: Boolean,
    popularityEnabled: Boolean,
    onPopularityChange: (Boolean) -> Unit,
    onServerArchiveChange: (Boolean) -> Unit,
    pendingUploads: Int,
    syncedTracks: Int,
    localTracks: Int,
    incognito: Boolean,
    serverTotalTracks: Int,
    syncProgress: SyncProgress,
    filesLandInNavidrome: Boolean,
    onSyncNow: () -> Unit,
    onReviewDeletions: () -> Unit,
    canDelete: Boolean
) {
    item(key = "agro") {
        SettingsRow(
            title = "Agro Device",
            subtitle = connection.describe(devicePetname, server, paired),
            // A rejected token cannot be unpaired from — there is nothing on the server left to
            // unregister — so that row leads back to pairing instead.
            onClick = when {
                connection is AgroConnectionState.Rejected -> onPair
                paired -> onUnpair
                else -> onPair
            },
            destructive = connection is AgroConnectionState.Rejected
        )
    }

    if (!paired) return

    item(key = "agro_sync") {
        SettingsToggle(
            title = "Sync settings with Agro",
            subtitle = "Share the Navidrome address between devices.",
            checked = syncSettings && !incognito,
            onCheckedChange = onSyncSettingsChange,
            enabled = !incognito
        )
    }

    item(key = "agro_popularity") {
        SettingsToggle(
            title = "Contribute to \u201cPopular on Agro\u201d",
            // Says what leaves the device and who ends up able to see it. "Anonymous" alone would
            // be the sort of reassurance that is technically true and still misleading: the server
            // already knows this account's plays from scrobbling, and what changes here is that
            // other accounts on it can see the total.
            subtitle = "Adds play counts to the server's shared totals, with no account or times attached. " +
                "Other people on this server see the totals, not you. The shelf works either way.",
            checked = popularityEnabled && !incognito,
            onCheckedChange = onPopularityChange,
            enabled = !incognito
        )
    }

    agroDevicesSection(
        devices = devices,
        handoff = handoff,
        isResuming = isResuming,
        onResume = onResume
    )

    librarySyncSection(
        p2pEnabled = p2pSyncEnabled,
        onP2pEnabledChange = onP2pSyncChange,
        archiveEnabled = serverArchiveEnabled,
        canArchive = canArchive,
        onArchiveEnabledChange = onServerArchiveChange,
        pendingCount = pendingUploads,
        syncedCount = syncedTracks,
        progress = syncProgress,
        // Describes what the switches above actually do. Both of these name the *server* storing
        // your files, which is archiving — with archiving off, nothing is stored there at all and
        // the line was simply untrue.
        serverSummary = when {
            !serverArchiveEnabled ->
                "Direct peer-to-peer sharing."
            filesLandInNavidrome -> "Archived to Navidrome."
            else -> "Archived to Agro server."
        },
        onSyncNow = onSyncNow,
        onReviewDeletions = onReviewDeletions,
        canDelete = canDelete,
        localTrackCount = localTracks,
        incognito = incognito,
        serverTotalTracks = serverTotalTracks
    )
}

/**
 * One line for the connection row.
 *
 * This row used to read the same whether the credential worked or not, which made being signed out
 * by the server indistinguishable from working normally. [AgroConnectionState.Unreachable] stays
 * deliberately vague: a failed check proves nothing about the credential, only that we could not
 * ask, and claiming otherwise would send the user to re-pair a pairing that is fine.
 */
private fun AgroConnectionState.describe(
    devicePetname: String,
    server: String,
    paired: Boolean
): String = when (this) {
    is AgroConnectionState.Unpaired ->
        if (paired) "$devicePetname • $server"
        else "Not paired — scan the pairing QR, or tap to enter a server"
    is AgroConnectionState.Checking -> "$server • Checking…"
    is AgroConnectionState.Connected -> "$username • $devicePetname • $server"
    is AgroConnectionState.Rejected ->
        "Signed out by $server — this device's token was revoked. Tap to pair again."
    is AgroConnectionState.NotActive -> "$server • $detail"
    is AgroConnectionState.Unreachable ->
        "$devicePetname • $server • Could not reach the server."
}
