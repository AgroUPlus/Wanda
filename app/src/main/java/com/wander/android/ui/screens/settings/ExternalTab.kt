package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.externalTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    // ── Playlist Importer Hero Card ──────────────────────────────────────────────────────────
    item(key = "import_playlist_card") {
        PlaylistImportHeroCard(onOpenImport = actions.onOpenImport)
    }

    // ── External Sharing Section ────────────────────────────────────────────────────────────
    if (state.incognito) {
        item(key = "sharing_incognito") {
            SettingsRow(
                title = "Sharing paused by incognito",
                subtitle = "Custom sharing links are off while incognito is enabled in Privacy."
            )
        }
    }

    item(key = "share_domain") {
        SettingsRow(
            title = "Custom share domain",
            subtitle = when {
                state.agroShareDomain.isNotBlank() -> "${state.agroShareDomain}/listen — configured on Agro server"
                state.shareDomain.isNotBlank() -> "Links go out as ${state.shareDomain}/listen — tap to customize"
                else -> "Default — shares each backend's native link"
            },
            onClick = actions.onEditShareDomain,
            enabled = !state.incognito
        )
    }

    // ── Backup ──────────────────────────────────────────────────────────────────────────────
    item(key = "backup") {
        BackupSection()
    }

    item(key = "artist_release_notifications") {
        SettingsToggle(
            title = "New music from artists you follow",
            subtitle = "Checks every few hours on Wi-Fi and tells you when someone you follow " +
                "puts something out. Follow an artist from their page. Off by default: it is a " +
                "network call you did not ask for, and a notification you did not ask for.",
            checked = state.artistReleaseNotificationsEnabled,
            onCheckedChange = actions.onArtistReleaseNotificationsChange
        )
    }
}
