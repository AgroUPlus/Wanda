package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.wander.android.data.sources.agro.AgroVisibility

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
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    agroPaired: Boolean,
    visibility: AgroVisibility?,
    onVisibilityChange: (AgroVisibility) -> Unit,
    proxyEnabled: Boolean,
    onProxyChange: (Boolean) -> Unit,
    onForgetEverything: () -> Unit
) {
    item(key = "incognito") {
        SettingsToggle(
            title = "Incognito",
            subtitle = "Stop recording plays, and stop telling anyone what you are listening " +
                "to. Everything below is off while this is on.",
            checked = incognito,
            onCheckedChange = onIncognitoChange
        )
    }

    if (agroPaired && visibility != null) {
        item(key = "visibility_header") { SettingsSection("What friends can see") }

        // Greyed out rather than hidden while incognito is on. The stored preferences are left
        // exactly as they were and come back untouched when it goes off — incognito overrides
        // them for as long as it is on, it does not rewrite them. Hiding the rows instead would
        // leave no way to tell what will be shared again afterwards.
        if (incognito) {
            item(key = "visibility_incognito_note") {
                SettingsRow(
                    subtitle = "Incognito is on, so none of this is being shared. " +
                        "Your choices are kept for when you turn it off.",
                    title = "Paused by incognito"
                )
            }
        }

        item(key = "show_now_playing") {
            SettingsToggle(
                title = "Show what I'm playing",
                subtitle = "Friends see your current track, and can listen along with you",
                checked = visibility.showNowPlaying && !incognito,
                onCheckedChange = {
                    onVisibilityChange(visibility.copy(showNowPlaying = it))
                },
                enabled = !incognito
            )
        }

        item(key = "show_stats") {
            SettingsToggle(
                title = "Share my listening stats",
                subtitle = "Friends see your top artists and how much your taste overlaps theirs",
                checked = visibility.showStats && !incognito,
                onCheckedChange = { onVisibilityChange(visibility.copy(showStats = it)) },
                enabled = !incognito
            )
        }

        item(key = "discoverable") {
            SettingsToggle(
                title = "Let people find me",
                subtitle = "Your username appears when someone searches for it. Off means only " +
                    "people you have already added can see you at all.",
                checked = visibility.discoverable && !incognito,
                onCheckedChange = { onVisibilityChange(visibility.copy(discoverable = it)) },
                enabled = !incognito
            )
        }
    }

    if (agroPaired) {
        item(key = "proxy_relay") {
            SettingsToggle(
                title = "Agro Privacy Relay",
                subtitle = "Route metadata and lyric requests through your Agro server to mask your IP from external services like LRCLIB and Archive.org.",
                checked = proxyEnabled,
                onCheckedChange = onProxyChange
            )
        }
    }

    item(key = "forget") {
        SettingsRow(
            title = "Forget all credentials",
            subtitle = "Signs out of every source and erases stored secrets",
            onClick = onForgetEverything,
            destructive = true
        )
    }
}
