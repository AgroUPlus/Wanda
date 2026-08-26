package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.wander.android.core.update.UpdateCheckResult

/**
 * App identity and the manual update check.
 *
 * No background polling: the app is battery-first, and a version check is only ever worth a
 * network round trip when someone is looking at this row.
 */
private const val ORG_URL = "https://github.com/AgroUPlus"

internal fun LazyListScope.aboutTab(
    appVersion: String,
    updateCheck: UpdateCheckResult?,
    isChecking: Boolean,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    item(key = "version") {
        SettingsRow(
            title = "Version",
            subtitle = appVersion
        )
    }

    item(key = "check_for_update") {
        SettingsRow(
            title = "Check for update",
            subtitle = when {
                isChecking -> "Checking…"
                updateCheck is UpdateCheckResult.UpdateAvailable ->
                    "Version ${updateCheck.version} is available — tap to view"
                updateCheck is UpdateCheckResult.UpToDate -> "You're on the latest version"
                updateCheck is UpdateCheckResult.Failed -> "Couldn't check — tap to retry"
                else -> "Tap to check for a newer version"
            },
            onClick = {
                val available = updateCheck
                if (available is UpdateCheckResult.UpdateAvailable) {
                    onOpenRelease(available.releaseUrl)
                } else {
                    onCheckForUpdate()
                }
            }
        )
    }

    item(key = "credits_header") { SettingsSection("Credits") }

    item(key = "credits_org") {
        SettingsRow(
            title = "AgroUPlus",
            subtitle = "Wanda and Agro are built here. Source, issues and releases on GitHub.",
            onClick = { onOpenUrl(ORG_URL) }
        )
    }

    item(key = "credits_sources") {
        SettingsRow(
            title = "Standing on",
            // Named rather than summarised as "open source libraries": these are the projects
            // whose work the app is made of, and a list that names nobody credits nobody.
            subtitle = "Media3 · Jetpack Compose · Room · Ktor · Coil · Hilt · " +
                "the Subsonic API, and Navidrome's implementation of it"
        )
    }

    item(key = "credits_licence") {
        SettingsRow(
            title = "Licence",
            subtitle = "GPL-3.0. No telemetry, no analytics, no accounts of our own."
        )
    }

    item(key = "auto_update_check") {
        SettingsToggle(
            title = "Check for updates on launch",
            subtitle = "Finds the latest release automatically and tells you when there is one. " +
                "Off by default: this is a network call at startup.",
            checked = autoUpdateCheck,
            onCheckedChange = onAutoUpdateCheckChange
        )
    }
}
