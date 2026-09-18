package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.ui.components.GroupedCard
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
    item(key = "about") {
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(0))) {
            SettingsRow(
                title = stringResource(R.string.common_duplicate_recordings),
                subtitle = stringResource(R.string.settings_review_which_tracks_same_recording),
                onClick = actions.onOpenMergePreview,
                icon = Icons.Rounded.ContentCopy
            )
            // Version and the update check are one row: the version is the question "am I
            // current?" and the check is the answer, so splitting them made the user tap two
            // rows to learn one thing.
            SettingsRow(
                title = stringResource(R.string.settings_version),
                subtitle = when {
                    state.updateCheck is UpdateCheckResult.UpdateAvailable ->
                        "Update available — ${state.updateCheck.version}"
                    state.updateCheck is UpdateCheckResult.Failed -> "Couldn't check for updates"
                    else -> state.appVersion
                },
                onClick = {
                    val available = state.updateCheck
                    if (available is UpdateCheckResult.UpdateAvailable) {
                        actions.onOpenUrl(available.releaseUrl)
                    } else {
                        actions.onCheckForUpdate()
                    }
                },
                icon = Icons.Rounded.Info
            )
        }
    }

    item(key = "credits_header") { SettingsSection(stringResource(R.string.settings_section_credits)) }
    item(key = "credits") {
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(1))) {
            SettingsRow(
                title = stringResource(R.string.settings_agrouplus),
                subtitle = stringResource(R.string.settings_wanda_agro_built_here_source),
                onClick = { actions.onOpenUrl(ORG_URL) }
            )
            SettingsToggle(
                title = stringResource(R.string.settings_check_updates_launch),
                subtitle = stringResource(R.string.settings_finds_latest_release_automatically_tells),
                checked = state.autoUpdateCheckEnabled,
                onCheckedChange = actions.onAutoUpdateCheckChange,
                icon = Icons.Rounded.Update
            )
        }
    }
}
