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
private const val REPO_URL = "https://github.com/AgroUPlus/Wanda"

internal fun LazyListScope.aboutTab(
    appVersion: String,
    updateCheck: UpdateCheckResult?,
    isChecking: Boolean,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMergePreview: () -> Unit
) {
    item(key = "merge_preview") {
        SettingsRow(
            title = "Merge preview",
            subtitle = "See which of your tracks are the same recording, before anything changes",
            onClick = onOpenMergePreview
        )
    }

    item(key = "version") {
        SettingsRow(
            title = "Version",
            subtitle = appVersion,
            onClick = { onOpenUrl(REPO_URL) }
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
