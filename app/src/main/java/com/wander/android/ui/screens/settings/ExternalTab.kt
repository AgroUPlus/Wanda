package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.runtime.Composable
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
        val sharingItems = buildList<@Composable () -> Unit> {
            if (state.incognito) {
                add {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(0)),
                        title = stringResource(R.string.settings_sharing_paused_incognito),
                        subtitle = stringResource(R.string.settings_custom_sharing_links_off_while)
                    )
                }
            }
            add {
                SettingsRow(
                    modifier = Modifier.scale(rememberShelfEntranceScale(1)),
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
        GroupedCard(items = sharingItems)
    }

    item(key = "backup") {
        BackupSection()
    }

    item(key = "sec_notifications") { SettingsSection(stringResource(R.string.settings_section_notifications)) }
    item(key = "artist_release_notifications") {
        GroupedCard(
            items = listOf<@Composable () -> Unit>({
                SettingsToggle(
                    modifier = Modifier.scale(rememberShelfEntranceScale(2)),
                    title = stringResource(R.string.settings_new_music_from_artists_follow),
                    subtitle = stringResource(R.string.settings_checks_every_few_hours_wi),
                    checked = state.artistReleaseNotificationsEnabled,
                    onCheckedChange = actions.onArtistReleaseNotificationsChange,
                    icon = Icons.Rounded.NewReleases
                )
            })
        )
    }
}
