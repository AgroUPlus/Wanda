package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.privacyTab(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
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

    item(key = "forget") {
        SettingsRow(
            title = "Forget all credentials",
            subtitle = "Signs out of every source and erases stored secrets",
            onClick = onForgetEverything,
            destructive = true
        )
    }
}
