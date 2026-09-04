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
    item(key = "duplicate_recordings") {
        SettingsRow(
            title = "Duplicate recordings",
            subtitle = "Review which of your tracks are the same recording, before anything merges",
            onClick = actions.onOpenMergePreview
        )
    }

    // Version and the update check are one row: the version is the question "am I current?" and
    // the check is the answer, so splitting them made the user tap two rows to learn one thing.
    item(key = "version") {
        SettingsRow(
            title = "Version",
            subtitle = when {
                state.isCheckingForUpdate -> "${state.appVersion} · checking…"
                state.updateCheck is UpdateCheckResult.UpdateAvailable ->
                    "${state.appVersion} · version ${state.updateCheck.version} is out — tap to view"
                state.updateCheck is UpdateCheckResult.UpToDate ->
                    "${state.appVersion} · up to date"
                state.updateCheck is UpdateCheckResult.Failed ->
                    "${state.appVersion} · couldn't check — tap to retry"
                else -> "${state.appVersion} · tap to check for an update"
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
