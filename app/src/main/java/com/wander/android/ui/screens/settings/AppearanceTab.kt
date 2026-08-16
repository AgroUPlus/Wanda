package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.appearanceTab(
    monet: Boolean,
    onMonetChange: (Boolean) -> Unit,
    amoled: Boolean,
    onAmoledChange: (Boolean) -> Unit
) {
    item(key = "monet") {
        SettingsToggle(
            title = "Match system colours",
            subtitle = "Use the wallpaper palette (Android 12+)",
            checked = monet,
            onCheckedChange = onMonetChange
        )
    }

    item(key = "amoled") {
        SettingsToggle(
            title = "True black",
            subtitle = "Unlit pixels on OLED screens use no power",
            checked = amoled,
            onCheckedChange = onAmoledChange
        )
    }
}
