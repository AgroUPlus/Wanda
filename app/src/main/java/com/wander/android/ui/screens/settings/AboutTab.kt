package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.ui.components.rememberShelfEntranceScale

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
    var i = 0

    val duplicateRecordingsIndex = i++
    item(key = "duplicate_recordings") {
        SettingsRow(
            title = stringResource(R.string.common_duplicate_recordings),
            subtitle = stringResource(R.string.settings_review_which_tracks_same_recording),
            onClick = actions.onOpenMergePreview,
            modifier = Modifier.scale(rememberShelfEntranceScale(duplicateRecordingsIndex))
        )
    }

    // Version and the update check are one row: the version is the question "am I current?" and
    // the check is the answer, so splitting them made the user tap two rows to learn one thing.
    val versionIndex = i++
    item(key = "version") {
        SettingsRow(
            title = stringResource(R.string.settings_version),
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
            },
            modifier = Modifier.scale(rememberShelfEntranceScale(versionIndex))
        )
    }

    val creditsHeaderIndex = i++
    item(key = "credits_header") {
        SettingsSection("Credits", modifier = Modifier.scale(rememberShelfEntranceScale(creditsHeaderIndex)))
    }

    val creditsOrgIndex = i++
    item(key = "credits_org") {
        SettingsRow(
            title = stringResource(R.string.settings_agrouplus),
            subtitle = stringResource(R.string.settings_wanda_agro_built_here_source),
            onClick = { actions.onOpenUrl(ORG_URL) },
            modifier = Modifier.scale(rememberShelfEntranceScale(creditsOrgIndex))
        )
    }

    val autoUpdateCheckIndex = i++
    item(key = "auto_update_check") {
        SettingsToggle(
            title = stringResource(R.string.settings_check_updates_launch),
            subtitle = stringResource(R.string.settings_finds_latest_release_automatically_tells),
            checked = state.autoUpdateCheckEnabled,
            onCheckedChange = actions.onAutoUpdateCheckChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(autoUpdateCheckIndex))
        )
    }

}
