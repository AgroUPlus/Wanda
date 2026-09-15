package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * What this device records, and what other people are allowed to see.
 *
 * The visibility switches only appear with an Agro server paired: without one there is nobody they
 * could reveal anything to, and a switch that cannot do anything is worse than no switch.
 *
 * All three default off, on the server as well as here. A privacy setting that defaults open has
 * already leaked by the time the user finds it.
 */
internal fun LazyListScope.privacyTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    var i = 0

    val incognitoIndex = i++
    item(key = "incognito") {
        SettingsToggle(
            title = "Incognito",
            subtitle = "Stop recording plays, and stop telling anyone what you are listening " +
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
                    subtitle = "Incognito is on, so none of this is being shared. " +
                        "Your choices are kept for when you turn it off.",
                    title = "Paused by incognito",
                    modifier = Modifier.scale(rememberShelfEntranceScale(visibilityIncognitoNoteIndex))
                )
            }
        }

        val showNowPlayingIndex = i++
        item(key = "show_now_playing") {
            SettingsToggle(
                title = "Show what I'm playing",
                subtitle = "Friends see your current track, and can listen along with you",
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
                title = "Share my listening stats",
                subtitle = "Friends see your top artists and how much your taste overlaps theirs",
                checked = state.agroVisibility.showStats && !state.incognito,
                onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(showStats = it)) },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(showStatsIndex))
            )
        }

        val discoverableIndex = i++
        item(key = "discoverable") {
            SettingsToggle(
                title = "Let people find me",
                subtitle = "Your username appears when someone searches for it. Off means only " +
                    "people you have already added can see you at all.",
                checked = state.agroVisibility.discoverable && !state.incognito,
                onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(discoverable = it)) },
                enabled = !state.incognito,
                modifier = Modifier.scale(rememberShelfEntranceScale(discoverableIndex))
            )
        }
    }

    if (state.agroPaired) {
        val proxyRelayIndex = i++
        item(key = "proxy_relay") {
            SettingsToggle(
                title = "Agro Privacy Relay",
                subtitle = "Route metadata and lyric requests through your Agro server to mask your IP from external services like LRCLIB and Archive.org.",
                checked = state.agroProxyEnabled,
                onCheckedChange = actions.onProxyChange,
                modifier = Modifier.scale(rememberShelfEntranceScale(proxyRelayIndex))
            )
        }
    }

    val forgetIndex = i++
    item(key = "forget") {
        SettingsRow(
            title = "Forget all credentials",
            subtitle = "Signs out of every source and erases stored secrets",
            onClick = actions.onForgetEverything,
            destructive = true,
            modifier = Modifier.scale(rememberShelfEntranceScale(forgetIndex))
        )
    }
}
