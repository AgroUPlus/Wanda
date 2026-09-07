package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

/**
 * Everything Agro: the pairing itself, then the two things a pairing buys — settings shared between
 * devices, and music shared between them.
 *
 * Nothing below the pairing row exists until there is a server to talk to. A toggle that cannot do
 * anything is worse than no toggle.
 */
internal fun LazyListScope.syncTab(
    state: SettingsUiState,
    actions: SettingsActions,
    devices: AgroDevicesState
) {
    item(key = "agro") {
        SettingsRow(
            title = "Agro Device",
            subtitle = state.agroConnection.describe(
                state.agroDevicePetname,
                state.agroServer,
                state.agroPaired
            ),
            // A rejected token cannot be unpaired from — there is nothing on the server left to
            // unregister — so that row leads back to pairing instead.
            onClick = when {
                state.agroConnection is AgroConnectionState.Rejected -> actions.onAgroPair
                state.agroPaired -> actions.onAgroUnpair
                else -> actions.onAgroPair
            },
            destructive = state.agroConnection is AgroConnectionState.Rejected
        )
    }

    if (!state.agroPaired) return

    item(key = "agro_sync") {
        SettingsToggle(
            title = "Sync settings with Agro",
            subtitle = "Share the Navidrome address between devices.",
            checked = state.agroSyncSettings && !state.incognito,
            onCheckedChange = actions.onSyncSettingsChange,
            enabled = !state.incognito
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
            checked = state.popularityContribution && !state.incognito,
            onCheckedChange = actions.onPopularityChange,
            enabled = !state.incognito
        )
    }

    item(key = "agro_catalog_trade") {
        SettingsToggle(
            title = "Trade fingerprints with the server",
            // Names both directions and who ends up able to see it. The catalogue has no account
            // column, so publishing is a disclosure to everyone on the server, not just to it.
            subtitle = "Sends the acoustic fingerprints of your tracks and takes everyone else's, " +
                "so badly tagged music inherits good tags. Other people on this server can see " +
                "which recordings you hold, not your listening. Recognition works either way.",
            checked = state.catalogTrade,
            onCheckedChange = actions.onCatalogTradeChange
        )
    }

    agroDevicesSection(state = devices, onResume = actions.onResumeHandoff)

    librarySyncSection(
        state = state,
        actions = actions,
        // Describes what the switches above actually do. Both of these name the *server* storing
        // your files, which is archiving — with archiving off, nothing is stored there at all and
        // the line was simply untrue.
        serverSummary = when {
            !state.serverArchive -> "Direct peer-to-peer sharing."
            // `navidrome` is the "files land in Navidrome" condition: with a Navidrome connected,
            // the archive is that server rather than Agro's own storage.
            state.navidrome -> "Archived to Navidrome."
            else -> "Archived to Agro server."
        }
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
