package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.rememberShelfEntranceScale

internal fun LazyListScope.externalTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "import_playlist_card") {
        PlaylistImportHeroCard(onOpenImport = actions.onOpenImport)
    }

    item(key = "sharing") {
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(0))) {
            if (state.incognito) {
                SettingsRow(
                    title = stringResource(R.string.settings_sharing_paused_incognito),
                    subtitle = stringResource(R.string.settings_custom_sharing_links_off_while)
                )
            }
            SettingsRow(
                title = stringResource(R.string.settings_custom_share_domain),
                subtitle = when {
                    state.agroShareDomain.isNotBlank() -> "${state.agroShareDomain}/listen"
                    state.shareDomain.isNotBlank() -> "${state.shareDomain}/listen"
                    else -> "Default"
                },
                onClick = actions.onEditShareDomain,
                enabled = !state.incognito,
                icon = Icons.Rounded.Link
            )
        }
    }

    item(key = "backup") {
        BackupSection()
    }

    item(key = "artist_release_notifications") {
        GroupedCard(modifier = Modifier.scale(rememberShelfEntranceScale(1))) {
            SettingsToggle(
                title = stringResource(R.string.settings_new_music_from_artists_follow),
                subtitle = stringResource(R.string.settings_checks_every_few_hours_wi),
                checked = state.artistReleaseNotificationsEnabled,
                onCheckedChange = actions.onArtistReleaseNotificationsChange,
                icon = Icons.Rounded.NewReleases
            )
        }
    }
}
