package com.wander.android.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wander.android.R

/**
 * Light, dark, or whatever the phone is set to. Stored by [name] in `SecureDisplayPreferences`.
 *
 * Follows the system by default, which is what the app did before there was a choice.
 */
enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.settings_theme_mode_system),
    LIGHT(R.string.settings_theme_mode_light),
    DARK(R.string.settings_theme_mode_dark);

    /** Whether this mode draws the dark scheme right now. */
    @Composable
    fun isDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** The stored mode; a key never written (a fresh install) follows the system. */
        fun fromKey(key: String): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * Whether the app is drawing its dark scheme, provided by [WanderTheme].
 *
 * Read this instead of `isSystemInDarkTheme()` anywhere a surface picks its own tones: with a
 * forced Light or Dark theme the two disagree, and the phone's answer would tint a light screen
 * with dark-mode colours.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }
