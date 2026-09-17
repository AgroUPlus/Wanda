package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * What this device records, and what other people are allowed to see.
 *
 * The visibility switches only appear with an Agro server paired: without one there is nobody they
 * could reveal anything to, and a switch that cannot do anything is worse than no switch.
 *
 * The visibility switches default off, on the server as well as here — a privacy setting that
 * defaults open has already leaked by the time the user finds it. Popular on Agro is the one
 * exception, defaulting on: it never carries an account id, so there is no identity in it for a
 * default to leak.
 */
internal fun LazyListScope.privacyTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    var i = 0

    val incognitoIndex = i++
    item(key = "incognito") {
        SettingsToggle(
            title = stringResource(R.string.settings_incognito),
            subtitle = stringResource(R.string.settings_stop_recording_plays_stop_telling) +
                "to. Everything below is off while this is on.",
            checked = state.incognito,
            onCheckedChange = actions.onIncognitoChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(incognitoIndex))
        )
    }

    if (state.agroPaired && state.agroVisibility != null) {
        val visibilityHeaderIndex = i++
        item(key = "visibility_header") {
            SettingsSection(
                "What friends can see",
                modifier = Modifier.scale(rememberShelfEntranceScale(visibilityHeaderIndex))
            )
        }

        // Greyed out rather than hidden while state.incognito is on. The stored preferences are left
        // exactly as they were and come back untouched when it goes off — state.incognito overrides
        // them for as long as it is on, it does not rewrite them. Hiding the rows instead would
        // leave no way to tell what will be shared again afterwards.
        if (state.incognito) {
            val visibilityIncognitoNoteIndex = i++
            item(key = "visibility_incognito_note") {
                SettingsRow(
                    subtitle = stringResource(R.string.settings_incognito_so_none_being_shared) +
                        "Your choices are kept for when you turn it off.",
                    title = stringResource(R.string.settings_paused_incognito),
                    modifier = Modifier.scale(rememberShelfEntranceScale(visibilityIncognitoNoteIndex))
                )
            }
        }

        val showNowPlayingIndex = i++
        item(key = "show_now_playing") {
            SettingsToggle(
                title = stringResource(R.string.settings_show_what_i_m_playing),
                subtitle = stringResource(R.string.settings_friends_see_current_track_can),
                checked = state.agroVisibility.showNowPlaying && !state.incognito,
                onCheckedChange = {
                    actions.onVisibilityChange(state.agroVisibility.copy(showNowPlaying = it))
                },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(showNowPlayingIndex))
            )
        }

        val showStatsIndex = i++
        item(key = "show_stats") {
            SettingsToggle(
                title = stringResource(R.string.settings_share_my_listening_stats),
                subtitle = stringResource(R.string.settings_friends_see_top_artists_how),
                checked = state.agroVisibility.showStats && !state.incognito,
                onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(showStats = it)) },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(showStatsIndex))
            )
        }

        val discoverableIndex = i++
        item(key = "discoverable") {
            SettingsToggle(
                title = stringResource(R.string.settings_let_people_find_me),
                subtitle = stringResource(R.string.settings_username_appears_when_someone_searches) +
                    "people you have already added can see you at all.",
                checked = state.agroVisibility.discoverable && !state.incognito,
                onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(discoverable = it)) },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(discoverableIndex))
            )
        }

        val popularOptInIndex = i++
        item(key = "popular_opt_in") {
            SettingsToggle(
                title = stringResource(R.string.settings_popular_on_agro),
                subtitle = stringResource(R.string.settings_include_plays_in_the_servers_shared_chart),
                checked = state.agroVisibility.popularOptIn && !state.incognito,
                onCheckedChange = {
                    actions.onVisibilityChange(state.agroVisibility.copy(popularOptIn = it))
                },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(popularOptInIndex))
            )
        }
    }

    if (state.agroPaired) {
        val proxyRelayIndex = i++
        item(key = "proxy_relay") {
            SettingsToggle(
                title = stringResource(R.string.settings_agro_privacy_relay),
                subtitle = stringResource(R.string.settings_route_metadata_lyric_requests_through),
                checked = state.agroProxyEnabled,
                onCheckedChange = actions.onProxyChange,
                modifier = Modifier.scale(rememberShelfEntranceScale(proxyRelayIndex))
            )
        }
    }

    val forgetIndex = i++
    item(key = "forget") {
        SettingsRow(
            title = stringResource(R.string.settings_forget_all_credentials),
            subtitle = stringResource(R.string.settings_signs_out_every_source_erases),
            onClick = actions.onForgetEverything,
            destructive = true,
            modifier = Modifier.scale(rememberShelfEntranceScale(forgetIndex))
        )
    }
}
