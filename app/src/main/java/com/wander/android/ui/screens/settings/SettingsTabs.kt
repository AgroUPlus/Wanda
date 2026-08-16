package com.wander.android.ui.screens.settings

/**
 * The settings, grouped by the question being asked rather than by which subsystem answers it.
 *
 * Everything used to be one scroll of six unlabelled sections, so "where do I sign into Navidrome"
 * and "where do I turn off scrobbling" were both answered by scrolling and reading. The order is
 * deliberate: what the app is connected to, then what it does with those connections, then how it
 * looks, then the things you set once.
 */
internal enum class SettingsTab(val label: String) {
    CONNECTIONS("Connections"),
    SYNC("Sync"),
    APPEARANCE("Appearance"),
    PLAYBACK("Playback"),
    SHARING("Sharing"),
    PRIVACY("Privacy")
}
