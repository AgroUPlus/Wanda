package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.wander.android.core.update.UpdateCheckResult

/**
 * App identity and the manual update check.
 *
 * No background polling: the app is battery-first, and a version check is only ever worth a
 * network round trip when someone is looking at this row.
 */
internal fun LazyListScope.aboutTab(
    appVersion: String,
    updateCheck: UpdateCheckResult?,
    isChecking: Boolean,
    autoUpdateCheck: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenRelease: (String) -> Unit
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
                else -> "Tap to check the latest release on GitHub"
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

    item(key = "auto_update_check") {
        SettingsToggle(
            title = "Check for updates on launch",
            subtitle = "Looks up the latest GitHub release each time you open the app and shows " +
                "a popup if one is available. Off by default: this is a network call at startup.",
            checked = autoUpdateCheck,
            onCheckedChange = onAutoUpdateCheckChange
        )
    }
}
