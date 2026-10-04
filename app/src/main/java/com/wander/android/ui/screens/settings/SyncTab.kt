package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
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
    item(key = "agro") {
        GroupedCard(
            items = listOf<@Composable () -> Unit>({
                SettingsRow(
                    modifier = Modifier.scale(rememberShelfEntranceScale(0)),
                    title = stringResource(R.string.settings_agro_device),
                    subtitle = state.agroConnection.describe(state.agroDevicePetname, state.agroPaired),
                    // A rejected token cannot be unpaired from — there is nothing on the server
                    // left to unregister — so that row leads back to pairing instead.
                    onClick = when {
                        state.agroConnection is AgroConnectionState.Rejected -> actions.onAgroPair
                        state.agroPaired -> actions.onAgroUnpair
                        else -> actions.onAgroPair
                    },
                    destructive = state.agroConnection is AgroConnectionState.Rejected,
                    leading = { AgroBadge(SettingsCategory.SYNC.hue) }
                )
            })
        )
    }

    if (!state.agroPaired) return

    item(key = "agro_toggles") {
        val toggleItems = buildList<@Composable () -> Unit> {
            add {
                SettingsToggle(
                    modifier = Modifier.scale(rememberShelfEntranceScale(1)),
                    title = stringResource(R.string.settings_sync_settings_agro),
                    subtitle = stringResource(R.string.settings_share_navidrome_address_between_devices),
                    checked = state.agroSyncSettings && !state.incognito,
                    onCheckedChange = actions.onSyncSettingsChange,
                    enabled = !state.incognito,
                    icon = Icons.Rounded.SettingsSuggest
                )
            }
            add {
                SettingsToggle(
                    modifier = Modifier.scale(rememberShelfEntranceScale(2)),
                    title = stringResource(R.string.settings_contribute_u201cpopular_agro_u201d),
                    // Says what leaves the device and who ends up able to see it. "Anonymous"
                    // alone would be the sort of reassurance that is technically true and still
                    // misleading: the server already knows this account's plays from scrobbling,
                    // and what changes here is that other accounts on it can see the total.
                    subtitle = stringResource(R.string.settings_adds_play_counts_server_s),
                    checked = state.popularityContribution && !state.incognito,
                    onCheckedChange = actions.onPopularityChange,
                    enabled = !state.incognito,
                    icon = Icons.AutoMirrored.Rounded.TrendingUp
                )
            }
            add {
                SettingsToggle(
                    modifier = Modifier.scale(rememberShelfEntranceScale(3)),
                    title = stringResource(R.string.settings_improve_agro),
                    // Names both directions and who ends up able to see it. The catalogue has no
                    // account column, so publishing is a disclosure to everyone on the server,
                    // not just to it. Also covers lyrics: publishRecording/catalogSince carry
                    // lyrics text alongside the fingerprint under this same flag.
                    subtitle = stringResource(R.string.settings_sends_acoustic_fingerprints_lyrics_tracks),
                    checked = state.catalogTrade,
                    onCheckedChange = actions.onCatalogTradeChange,
                    icon = Icons.Rounded.Fingerprint
                )
            }
            if (state.catalogTrade) {
                // Follows the toggle rather than living inside it: the switch says what the
                // setting does, and this says what it has done. Only shown while it is on,
                // because a pair of zeroes under an off switch explains nothing.
                add {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(4)),
                        title = stringResource(R.string.settings_what_trade_has_done),
                        subtitle = tradeTotals(state.fingerprintsShared, state.lyricsReceived),
                        icon = Icons.Rounded.AutoAwesome
                    )
                }
            }
        }
        GroupedCard(
            modifier = Modifier.padding(top = 16.dp),
            items = toggleItems
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
            !state.serverArchive -> R.string.settings_sync_summary_peer
            // `navidrome` is the "files land in Navidrome" condition: with a Navidrome connected,
            // the archive is that server rather than Agro's own storage.
            state.navidrome -> R.string.settings_sync_summary_navidrome
            else -> R.string.settings_sync_summary_agro
        }
    )
}

/**
 * What the catalogue trade has amounted to, in both directions.
 *
 * Says "nothing yet" rather than "0 · 0" before the first sync: the trade only runs on a charger
 * over Wi-Fi, so a freshly enabled toggle showing zeroes is the normal case and reads as broken.
 */
@Composable
private fun tradeTotals(shared: Int, received: Int): String {
    val total = shared + received
    return if (total == 0) {
        stringResource(R.string.settings_trade_nothing_yet)
    } else {
        pluralStringResource(R.plurals.settings_trade_improved, total, total)
    }
}

/**
 * One line for the connection row.
 *
 * This row used to read the same whether the credential worked or not, which made being signed out
 * by the server indistinguishable from working normally. [AgroConnectionState.Unreachable] stays
 * deliberately vague: a failed check proves nothing about the credential, only that we could not
 * ask, and claiming otherwise would send the user to re-pair a pairing that is fine.
 */
@Composable
private fun AgroConnectionState.describe(
    devicePetname: String,
    paired: Boolean
): String = when (this) {
    is AgroConnectionState.Unpaired -> if (paired) devicePetname else stringResource(R.string.settings_agro_not_paired)
    is AgroConnectionState.Checking -> stringResource(R.string.settings_agro_checking)
    is AgroConnectionState.Connected -> devicePetname
    is AgroConnectionState.Rejected -> stringResource(R.string.settings_agro_signed_out_tap_to_pair)
    is AgroConnectionState.NotActive -> stringResource(R.string.agro_error_not_active)
    is AgroConnectionState.Unreachable -> stringResource(R.string.settings_agro_could_not_reach_server)
}
