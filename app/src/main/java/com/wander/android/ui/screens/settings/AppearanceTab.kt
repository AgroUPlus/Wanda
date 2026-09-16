package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.wander.android.ui.components.rememberShelfEntranceScale

internal fun LazyListScope.appearanceTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    // A running counter rather than a fixed literal per row: this list gains and loses rows over
    // time, and a hand-numbered index drifts the moment someone inserts one without renumbering
    // everything after it. Captured into a `val` per row at declaration time — inside the DSL
    // body, not inside a row's own composable lambda — so the index a row animates with can never
    // depend on the order Compose happens to invoke that lambda in.
    var i = 0

    val monetIndex = i++
    item(key = "monet") {
        SettingsToggle(
            title = "Match system colours",
            subtitle = "Use the wallpaper palette (Android 12+)",
            checked = state.monet,
            onCheckedChange = actions.onMonetChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(monetIndex))
        )
    }

    val coverArtThemeIndex = i++
    item(key = "cover_art_theme") {
        SettingsToggle(
            title = "Dynamic cover art theme",
            subtitle = "Subtly tint the app with colours from the album cover",
            checked = state.coverArtTheme,
            onCheckedChange = actions.onCoverArtThemeChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(coverArtThemeIndex))
        )
    }

    val amoledIndex = i++
    item(key = "amoled") {
        SettingsToggle(
            title = "True black",
            subtitle = "Unlit pixels on OLED screens use no power",
            checked = state.amoled,
            onCheckedChange = actions.onAmoledChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(amoledIndex))
        )
    }

    val immersivePlayerIndex = i++
    item(key = "immersive_player") {
        SettingsToggle(
            title = "Immersive player",
            subtitle = "Cover art fills the screen edge-to-edge when playing",
            checked = state.immersivePlayer,
            onCheckedChange = actions.onImmersivePlayerChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(immersivePlayerIndex))
        )
    }

    val reduceMotionIndex = i++
    item(key = "reduce_motion") {
        SettingsToggle(
            title = "Reduce motion",
            subtitle = if (state.systemReduceMotion) {
                "Already off — the phone's own Accessibility setting is on"
            } else {
                "Turn off springs, pops and transitions throughout the app"
            },
            checked = state.reduceMotion || state.systemReduceMotion,
            onCheckedChange = actions.onReduceMotionChange,
            enabled = !state.systemReduceMotion,
            modifier = Modifier.scale(rememberShelfEntranceScale(reduceMotionIndex))
        )
    }

    val letterByLetterLyricsIndex = i++
    item(key = "letter_by_letter_lyrics") {
        SettingsToggle(
            title = "Letter-by-letter lyrics",
            subtitle = "Sweep the active word in one letter at a time, karaoke-style. " +
                "Off highlights the whole line as it's sung.",
            checked = state.letterByLetterLyrics,
            onCheckedChange = actions.onLetterByLetterLyricsChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(letterByLetterLyricsIndex))
        )
    }
}
