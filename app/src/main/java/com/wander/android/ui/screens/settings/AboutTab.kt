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
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "merge_preview") {
        SettingsRow(
            title = "Merge preview",
            subtitle = "See which of your tracks are the same recording, before anything changes",
            onClick = actions.onOpenMergePreview
        )
    }

    item(key = "version") {
        SettingsRow(
            title = "Version",
            subtitle = state.appVersion,
            onClick = { actions.onOpenUrl(REPO_URL) }
        )
    }

    item(key = "check_for_update") {
        SettingsRow(
            title = "Check for update",
            subtitle = when {
                state.isCheckingForUpdate -> "Checking…"
                state.updateCheck is UpdateCheckResult.UpdateAvailable ->
                    "Version ${state.updateCheck.version} is available — tap to view"
                state.updateCheck is UpdateCheckResult.UpToDate -> "You're on the latest version"
                state.updateCheck is UpdateCheckResult.Failed -> "Couldn't check — tap to retry"
                else -> "Tap to check for a newer version"
            },
            onClick = {
                val available = state.updateCheck
                if (available is UpdateCheckResult.UpdateAvailable) {
                    actions.onOpenUrl(available.releaseUrl)
                } else {
                    actions.onCheckForUpdate()
                }
            }
        )
    }

    item(key = "credits_header") { SettingsSection("Credits") }

    item(key = "credits_org") {
        SettingsRow(
            title = "AgroUPlus",
            subtitle = "Wanda and Agro are built here. Source, issues and releases on GitHub.",
            onClick = { actions.onOpenUrl(ORG_URL) }
        )
    }

    item(key = "auto_update_check") {
        SettingsToggle(
            title = "Check for updates on launch",
            subtitle = "Finds the latest release automatically and tells you when there is one. " +
                "Off by default: this is a network call at startup.",
            checked = state.autoUpdateCheckEnabled,
            onCheckedChange = actions.onAutoUpdateCheckChange
        )
    }

    item(key = "release_notifications") {
        SettingsToggle(
            title = "Notify me about new releases",
            subtitle = "Checks once a day on Wi-Fi and posts a notification when a new version " +
                "is published. Off by default: it is a network call you did not ask for, and a " +
                "notification you did not ask for.",
            checked = state.releaseNotificationsEnabled,
            onCheckedChange = actions.onReleaseNotificationsChange
        )
    }
}
