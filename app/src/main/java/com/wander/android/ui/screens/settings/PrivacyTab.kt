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
    onForgetEverything: () -> Unit
) {
    item(key = "incognito") {
        SettingsToggle(
            title = "Incognito",
            subtitle = "Do not record play counts or scrobble to your server",
            checked = incognito,
            onCheckedChange = onIncognitoChange
        )
    }

    if (agroPaired && visibility != null) {
        item(key = "visibility_header") { SettingsSection("What friends can see") }

        item(key = "show_now_playing") {
            SettingsToggle(
                title = "Show what I'm playing",
                subtitle = "Friends see your current track, and can listen along with you",
                checked = visibility.showNowPlaying,
                onCheckedChange = {
                    onVisibilityChange(visibility.copy(showNowPlaying = it))
                }
            )
        }

        item(key = "show_stats") {
            SettingsToggle(
                title = "Share my listening stats",
                subtitle = "Friends see your top artists and how much your taste overlaps theirs",
                checked = visibility.showStats,
                onCheckedChange = { onVisibilityChange(visibility.copy(showStats = it)) }
            )
        }

        item(key = "discoverable") {
            SettingsToggle(
                title = "Let people find me",
                subtitle = "Your username appears when someone searches for it. Off means only " +
                    "people you have already added can see you at all.",
                checked = visibility.discoverable,
                onCheckedChange = { onVisibilityChange(visibility.copy(discoverable = it)) }
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
