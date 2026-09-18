package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.rememberShelfEntranceScale

internal fun LazyListScope.appearanceTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "appearance") {
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(0))) {
            LanguageSetting(
                currentTag = state.languageTag,
                onLanguageChange = actions.onLanguageChange,
                icon = Icons.Rounded.Language
            )
            SettingsToggle(
                title = stringResource(R.string.settings_match_system_colours),
                subtitle = stringResource(R.string.settings_use_wallpaper_palette_android_12),
                checked = state.monet,
                onCheckedChange = actions.onMonetChange,
                icon = Icons.Rounded.Palette
            )
            SettingsToggle(
                title = stringResource(R.string.settings_dynamic_cover_art_theme),
                subtitle = stringResource(R.string.settings_subtly_tint_app_colours_from),
                checked = state.coverArtTheme,
                onCheckedChange = actions.onCoverArtThemeChange,
                icon = Icons.Rounded.AutoAwesome
            )
            SettingsToggle(
                title = stringResource(R.string.settings_true_black),
                subtitle = stringResource(R.string.settings_unlit_pixels_oled_screens_use),
                checked = state.amoled,
                onCheckedChange = actions.onAmoledChange,
                icon = Icons.Rounded.Contrast
            )
            SettingsToggle(
                title = stringResource(R.string.settings_immersive_player),
                subtitle = stringResource(R.string.settings_cover_art_fills_screen_edge),
                checked = state.immersivePlayer,
                onCheckedChange = actions.onImmersivePlayerChange,
                icon = Icons.Rounded.FullscreenExit
            )
            SettingsToggle(
                title = stringResource(R.string.settings_reduce_motion),
                subtitle = if (state.systemReduceMotion) {
                    "Already off"
                } else {
                    "Turn off springs, pops and transitions throughout the app"
                },
                checked = state.reduceMotion || state.systemReduceMotion,
                onCheckedChange = actions.onReduceMotionChange,
                enabled = !state.systemReduceMotion,
                icon = Icons.Rounded.MotionPhotosOff
            )
            SettingsToggle(
                title = stringResource(R.string.settings_letter_letter_lyrics),
                subtitle = stringResource(R.string.settings_sweep_active_word_one_letter),
                checked = state.letterByLetterLyrics,
                onCheckedChange = actions.onLetterByLetterLyricsChange,
                icon = Icons.Rounded.Subtitles
            )
        }
    }
}
