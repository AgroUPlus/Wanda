package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard

/**
 * Look and feel, in titled sections — one heading and one card each, so a new kind of setting
 * (which tabs or shelves a screen shows, say) is a new section rather than a longer single card.
 */
internal fun LazyListScope.appearanceTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    lookSection(key = "home", title = R.string.settings_section_home) {
        listOf {
            SettingsRow(
                title = stringResource(R.string.settings_customize_home),
                subtitle = stringResource(R.string.settings_customize_home_sub),
                onClick = actions.onCustomizeHome,
                icon = Icons.Rounded.Dashboard
            )
        }
    }

    lookSection(key = "navigation", title = R.string.settings_section_navigation) {
        listOf({ DockLayoutRow() }, { LibrarySectionsRow() })
    }

    lookSection(key = "language", title = R.string.settings_section_language) {
        listOf {
            LanguageSetting(
                currentTag = state.languageTag,
                onLanguageChange = actions.onLanguageChange,
                icon = Icons.Rounded.Language
            )
        }
    }

    lookSection(key = "colours", title = R.string.settings_section_colours) {
        listOf(
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_match_system_colours),
                    subtitle = stringResource(R.string.settings_use_wallpaper_palette_android_12),
                    checked = state.monet,
                    onCheckedChange = actions.onMonetChange,
                    icon = Icons.Rounded.Palette
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_dynamic_cover_art_theme),
                    subtitle = stringResource(R.string.settings_subtly_tint_app_colours_from),
                    checked = state.coverArtTheme,
                    onCheckedChange = actions.onCoverArtThemeChange,
                    icon = Icons.Rounded.AutoAwesome
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_true_black),
                    subtitle = stringResource(R.string.settings_unlit_pixels_oled_screens_use),
                    checked = state.amoled,
                    onCheckedChange = actions.onAmoledChange,
                    icon = Icons.Rounded.Contrast
                )
            }
        )
    }

    lookSection(key = "motion", title = R.string.settings_section_motion_effects) {
        listOf(
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_reduce_motion),
                    subtitle = stringResource(
                        if (state.systemReduceMotion) R.string.settings_reduce_motion_already_off else R.string.settings_reduce_motion_sub
                    ),
                    checked = state.reduceMotion || state.systemReduceMotion,
                    onCheckedChange = actions.onReduceMotionChange,
                    enabled = !state.systemReduceMotion,
                    icon = Icons.Rounded.MotionPhotosOff
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_back_blur),
                    subtitle = stringResource(R.string.settings_back_blur_subtitle),
                    checked = state.backBlur,
                    onCheckedChange = actions.onBackBlurChange,
                    icon = Icons.Rounded.BlurOn
                )
            }
        )
    }

    lookSection(key = "now_playing", title = R.string.settings_section_now_playing) {
        listOf(
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_immersive_player),
                    subtitle = stringResource(R.string.settings_cover_art_fills_screen_edge),
                    checked = state.immersivePlayer,
                    onCheckedChange = actions.onImmersivePlayerChange,
                    icon = Icons.Rounded.FullscreenExit
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_letter_letter_lyrics),
                    subtitle = stringResource(R.string.settings_sweep_active_word_one_letter),
                    checked = state.letterByLetterLyrics,
                    onCheckedChange = actions.onLetterByLetterLyricsChange,
                    icon = Icons.Rounded.Subtitles
                )
            }
        )
    }
}

/** A heading and its card, entering in turn with the sections above it. */
private fun LazyListScope.lookSection(
    key: String,
    @StringRes title: Int,
    rows: @Composable () -> List<@Composable () -> Unit>
) {
    item(key = "look_$key") {
        Column {
            SettingsSection(stringResource(title))
            GroupedCard(items = rows())
        }
    }
}
