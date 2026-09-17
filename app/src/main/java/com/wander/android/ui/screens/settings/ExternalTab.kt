package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.rememberShelfEntranceScale

internal fun LazyListScope.externalTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    var i = 0

    // ── Playlist Importer Hero Card ──────────────────────────────────────────────────────────
    item(key = "import_playlist_card") {
        PlaylistImportHeroCard(onOpenImport = actions.onOpenImport)
    }

    // ── External Sharing Section ────────────────────────────────────────────────────────────
    if (state.incognito) {
        val sharingIncognitoIndex = i++
        item(key = "sharing_incognito") {
            SettingsRow(
                title = stringResource(R.string.settings_sharing_paused_incognito),
                subtitle = stringResource(R.string.settings_custom_sharing_links_off_while),
                modifier = Modifier.scale(rememberShelfEntranceScale(sharingIncognitoIndex))
            )
        }
    }

    val shareDomainIndex = i++
    item(key = "share_domain") {
        SettingsRow(
            title = stringResource(R.string.settings_custom_share_domain),
            subtitle = when {
                state.agroShareDomain.isNotBlank() -> "${state.agroShareDomain}/listen — configured on Agro server"
                state.shareDomain.isNotBlank() -> "Links go out as ${state.shareDomain}/listen — tap to customize"
                else -> "Default — shares each backend's native link"
            },
            onClick = actions.onEditShareDomain,
            enabled = !state.incognito,
            modifier = Modifier.scale(rememberShelfEntranceScale(shareDomainIndex))
        )
    }

    // ── Backup ──────────────────────────────────────────────────────────────────────────────
    item(key = "backup") {
        BackupSection()
    }

    val artistReleaseNotificationsIndex = i++
    item(key = "artist_release_notifications") {
        SettingsToggle(
            title = stringResource(R.string.settings_new_music_from_artists_follow),
            subtitle = stringResource(R.string.settings_checks_every_few_hours_wi) +
                "puts something out. Follow an artist from their page. Off by default: it is a " +
                "network call you did not ask for, and a notification you did not ask for.",
            checked = state.artistReleaseNotificationsEnabled,
            onCheckedChange = actions.onArtistReleaseNotificationsChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(artistReleaseNotificationsIndex))
        )
    }
}
