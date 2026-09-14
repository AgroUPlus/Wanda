package com.wander.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings, as a list of places to go rather than a row of tabs to swipe through.
 *
 * The grouping is unchanged and deliberate — what the app is connected to, then what it does with
 * those connections, then how it looks, then the things you set once. What changed is that each
 * group is now a destination with a picture and a sentence, so the answer to "where do I turn off
 * scrobbling" is readable from the first screen instead of being behind a swipe.
 *
 * [hue] is the category's own colour, and it is a fixed value rather than a `colorScheme` role on
 * purpose: the point of this list is that the seven entries are told apart at a glance, and seven
 * shades of the same theme primary would not do that. It is used at low alpha for the badge and at
 * full strength for the glyph, which reads correctly on both a light and a dark surface without a
 * second palette — see [SettingsCategoryRow].
 */
internal enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val hue: Color
) {
    CONNECTIONS(
        label = "Connections",
        subtitle = "Navidrome, YouTube Music and your local files",
        icon = Icons.Rounded.CloudQueue,
        hue = Color(0xFF3B82F6)
    ),
    SYNC(
        label = "Sync",
        subtitle = "Devices, library syncing and what gets shared",
        icon = Icons.Rounded.Sync,
        hue = Color(0xFF06B6D4)
    ),
    APPEARANCE(
        label = "Look and feel",
        subtitle = "Change the theme and colours of the app",
        icon = Icons.Rounded.Palette,
        hue = Color(0xFF8B5CF6)
    ),
    PLAYBACK(
        label = "Playback and storage",
        subtitle = "Offline mode, downloads and the cache",
        icon = Icons.Rounded.PlayCircle,
        hue = Color(0xFFEC4899)
    ),
    EXTERNAL(
        label = "External",
        subtitle = "Import playlists and share what you are playing",
        icon = Icons.Rounded.SwapHoriz,
        hue = Color(0xFFF59E0B)
    ),
    PRIVACY(
        label = "Privacy",
        subtitle = "Incognito, visibility and what leaves this device",
        icon = Icons.Rounded.Lock,
        hue = Color(0xFF10B981)
    ),
    ABOUT(
        label = "About",
        subtitle = "Version, updates, duplicates and links",
        icon = Icons.Rounded.Info,
        hue = Color(0xFF64748B)
    );

    companion object {
        /** The category a route names, or null when the argument is not one of ours. */
        fun fromRoute(value: String?): SettingsCategory? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
