package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.sharingTab(
    shareDomain: String,
    agroShareDomain: String,
    onEditDomain: () -> Unit
) {
    item(key = "share_domain") {
        SettingsRow(
            title = "Custom share domain",
            subtitle = when {
                // Agro decides for the whole fleet when it has a domain, so saying what this
                // device was typed into would name a value that is not the one in use.
                agroShareDomain.isNotBlank() -> "$agroShareDomain/listen — set on your Agro server"
                shareDomain.isNotBlank() -> "Links go out as $shareDomain/listen — tap to change"
                else -> "Off — share each backend's own link"
            },
            onClick = onEditDomain
        )
    }
}
