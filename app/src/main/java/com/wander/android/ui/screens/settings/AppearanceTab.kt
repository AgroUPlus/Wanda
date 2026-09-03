package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.appearanceTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "monet") {
        SettingsToggle(
            title = "Match system colours",
            subtitle = "Use the wallpaper palette (Android 12+)",
            checked = state.monet,
            onCheckedChange = actions.onMonetChange
        )
    }

    item(key = "amoled") {
        SettingsToggle(
            title = "True black",
            subtitle = "Unlit pixels on OLED screens use no power",
            checked = state.amoled,
            onCheckedChange = actions.onAmoledChange
        )
    }
}
