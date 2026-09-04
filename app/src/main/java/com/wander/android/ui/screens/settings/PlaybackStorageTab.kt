package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.playbackStorageTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "sec_playback") { SettingsSection("Playback") }

    item(key = "offline") {
        SettingsToggle(
            title = "Offline mode",
            // Worth saying, because the app now flips this for you if you agree: without the
            // second sentence the toggle looks like it moved on its own.
            subtitle = "Only play what is already on this device. " +
                "Wanda offers to turn this on and off as your connection changes.",
            checked = state.offline,
            onCheckedChange = actions.onOfflineChange
        )
    }

    item(key = "preload_next") {
        SettingsToggle(
            title = "Ready the next track",
            // Said plainly, because it is a real cost and the honest reason to turn it off.
            subtitle = "Fetch the first couple of seconds ahead of time so skipping starts " +
                "instantly. Uses a little data on a track you might not play.",
            checked = state.preloadNext,
            onCheckedChange = actions.onPreloadNextChange
        )
    }

    item(key = "sec_measuring") { SettingsSection("Measuring") }

    item(key = "index_fingerprints") {
        SettingsRow(
            title = "Measure the library now",
            // Named for what it produces rather than for the machinery. "Fingerprint" means
            // nothing to most people; recognising a song and building a radio are the results.
            subtitle = "Lets Wanda recognise your songs and build radios from how they sound. " +
                "Otherwise this happens on Wi-Fi while charging.",
            onClick = actions.onIndexFingerprints
        )
    }

    item(key = "index_on_mobile_data") {
        SettingsToggle(
            title = "Measure over mobile data",
            // The cost stated in the units it is actually paid in. "Uses data" is not something
            // anyone can weigh; "a minute per song" is.
            subtitle = "Measuring a streamed song reads about a minute of it. On Wi-Fi that is " +
                "free, which is why this is off by default.",
            checked = state.indexOnMobileData,
            onCheckedChange = actions.onIndexOnMobileDataChange
        )
    }

    item(key = "view_fingerprints") {
        SettingsRow(
            title = "What has been measured",
            // Pause/resume of a running pass lives on the progress notification, not here: it is
            // an action on work in flight, and the notification is where that work is already
            // visible. This screen is the report; that one is the remote control.
            subtitle = "See which songs Wanda can recognise and hum-search, and which are still " +
                "waiting.",
            onClick = actions.onOpenFingerprints
        )
    }

    item(key = "sec_storage") { SettingsSection("Storage") }

    item(key = "download") {
        SettingsRow(
            title = "Download liked tracks now",
            subtitle = "Otherwise this happens on Wi-Fi while charging",
            onClick = actions.onDownloadLiked
        )
    }

    item(key = "cache") {
        SettingsRow(
            title = "Clear streaming cache",
            subtitle = formatBytes(state.cacheBytes) + " in use",
            onClick = actions.onClearCache
        )
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "0 MB"
}
