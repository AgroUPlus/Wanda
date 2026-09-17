package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
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

    val languageIndex = i++
    item(key = "language") {
        LanguageSetting(
            currentTag = state.languageTag,
            onLanguageChange = actions.onLanguageChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(languageIndex))
        )
    }

    val monetIndex = i++
    item(key = "monet") {
        SettingsToggle(
            title = stringResource(R.string.settings_match_system_colours),
            subtitle = stringResource(R.string.settings_use_wallpaper_palette_android_12),
            checked = state.monet,
            onCheckedChange = actions.onMonetChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(monetIndex))
        )
    }

    val coverArtThemeIndex = i++
    item(key = "cover_art_theme") {
        SettingsToggle(
            title = stringResource(R.string.settings_dynamic_cover_art_theme),
            subtitle = stringResource(R.string.settings_subtly_tint_app_colours_from),
            checked = state.coverArtTheme,
            onCheckedChange = actions.onCoverArtThemeChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(coverArtThemeIndex))
        )
    }

    val amoledIndex = i++
    item(key = "amoled") {
        SettingsToggle(
            title = stringResource(R.string.settings_true_black),
            subtitle = stringResource(R.string.settings_unlit_pixels_oled_screens_use),
            checked = state.amoled,
            onCheckedChange = actions.onAmoledChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(amoledIndex))
        )
    }

    val immersivePlayerIndex = i++
    item(key = "immersive_player") {
        SettingsToggle(
            title = stringResource(R.string.settings_immersive_player),
            subtitle = stringResource(R.string.settings_cover_art_fills_screen_edge),
            checked = state.immersivePlayer,
            onCheckedChange = actions.onImmersivePlayerChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(immersivePlayerIndex))
        )
    }

    val reduceMotionIndex = i++
    item(key = "reduce_motion") {
        SettingsToggle(
            title = stringResource(R.string.settings_reduce_motion),
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
            title = stringResource(R.string.settings_letter_letter_lyrics),
            subtitle = stringResource(R.string.settings_sweep_active_word_one_letter),
            checked = state.letterByLetterLyrics,
            onCheckedChange = actions.onLetterByLetterLyricsChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(letterByLetterLyricsIndex))
        )
    }
}
