package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.rememberShelfEntranceScale

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
    var i = 0

    val agroIndex = i++
    item(key = "agro") {
        SettingsRow(
            modifier = Modifier.scale(rememberShelfEntranceScale(agroIndex)),
            title = stringResource(R.string.settings_agro_device),
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
            destructive = state.agroConnection is AgroConnectionState.Rejected,
            leading = { SourceBadge(SourceIdentity.AGRO, SettingsCategory.SYNC.hue) }
        )
    }

    if (!state.agroPaired) return

    val agroSyncIndex = i++
    item(key = "agro_sync") {
        SettingsToggle(
            title = stringResource(R.string.settings_sync_settings_agro),
            subtitle = stringResource(R.string.settings_share_navidrome_address_between_devices),
            checked = state.agroSyncSettings && !state.incognito,
            onCheckedChange = actions.onSyncSettingsChange,
            enabled = !state.incognito,
            modifier = Modifier.scale(rememberShelfEntranceScale(agroSyncIndex))
        )
    }

    val agroPopularityIndex = i++
    item(key = "agro_popularity") {
        SettingsToggle(
            title = stringResource(R.string.settings_contribute_u201cpopular_agro_u201d),
            // Says what leaves the device and who ends up able to see it. "Anonymous" alone would
            // be the sort of reassurance that is technically true and still misleading: the server
            // already knows this account's plays from scrobbling, and what changes here is that
            // other accounts on it can see the total.
            subtitle = stringResource(R.string.settings_adds_play_counts_server_s) +
                "Other people on this server see the totals, not you. The shelf works either way.",
            checked = state.popularityContribution && !state.incognito,
            onCheckedChange = actions.onPopularityChange,
            enabled = !state.incognito,
            modifier = Modifier.scale(rememberShelfEntranceScale(agroPopularityIndex))
        )
    }

    val agroCatalogTradeIndex = i++
    item(key = "agro_catalog_trade") {
        SettingsToggle(
            title = stringResource(R.string.settings_improve_agro),
            // Names both directions and who ends up able to see it. The catalogue has no account
            // column, so publishing is a disclosure to everyone on the server, not just to it.
            // Also covers lyrics: publishRecording/catalogSince carry lyrics text alongside the
            // fingerprint under this same flag, so the label has to say so.
            subtitle = stringResource(R.string.settings_sends_acoustic_fingerprints_lyrics_tracks) +
                "everyone else's, so badly tagged music inherits good tags and lyrics. Other " +
                "people on this server can see which recordings and lyrics you hold, not your " +
                "listening. Recognition works either way.",
            checked = state.catalogTrade,
            onCheckedChange = actions.onCatalogTradeChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(agroCatalogTradeIndex))
        )
    }

    if (state.catalogTrade) {
        val tradeTotalsIndex = i++
        item(key = "agro_catalog_trade_totals") {
            // Follows the toggle rather than living inside it: the switch says what the setting
            // does, and this says what it has done. Only shown while it is on, because a pair of
            // zeroes under an off switch explains nothing.
            SettingsRow(
                title = stringResource(R.string.settings_what_trade_has_done),
                subtitle = tradeTotals(state.fingerprintsShared, state.lyricsReceived),
                modifier = Modifier.scale(rememberShelfEntranceScale(tradeTotalsIndex))
            )
        }
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
 * What the catalogue trade has amounted to, in both directions.
 *
 * Says "nothing yet" rather than "0 · 0" before the first sync: the trade only runs on a charger
 * over Wi-Fi, so a freshly enabled toggle showing zeroes is the normal case and reads as broken.
 */
private fun tradeTotals(shared: Int, received: Int): String {
    if (shared == 0 && received == 0) {
        return "Nothing traded yet. The first exchange runs while charging on Wi-Fi."
    }
    val sharedLabel = if (shared == 1) "1 fingerprint shared" else "$shared fingerprints shared"
    val receivedLabel = if (received == 1) "1 lyric received" else "$received lyrics received"
    return "$sharedLabel · $receivedLabel"
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
