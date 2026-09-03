package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.playbackStorageTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
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

    // Rows that act, not switches that describe.
    //
    // A switch says "this is how the app should behave from now on"; these say "stop what you are
    // doing" and "start again now", which is a thing done to work already in flight. Resuming also
    // re-enqueues rather than waiting for the next Wi-Fi-and-battery trigger, so the row does
    // visibly what its label promises — see `WorkControls.resume`.
    item(key = "pause_measuring") {
        SettingsRow(
            title = if (state.measuringPaused) "Resume measuring" else "Pause measuring",
            subtitle = if (state.measuringPaused) {
                "Measuring is paused. Resuming starts a pass now and retries anything that failed."
            } else {
                "Stops the current pass and keeps it stopped until you resume. " +
                    "Sync and downloads are unaffected."
            },
            onClick = { actions.onMeasuringPausedChange(!state.measuringPaused) }
        )
    }

    item(key = "pause_downloads") {
        SettingsRow(
            title = if (state.downloadingPaused) "Resume downloads" else "Pause downloads",
            subtitle = if (state.downloadingPaused) {
                "Downloading liked tracks is paused."
            } else {
                "Stops downloading liked tracks until you resume."
            },
            onClick = { actions.onDownloadingPausedChange(!state.downloadingPaused) }
        )
    }

    item(key = "view_fingerprints") {
        SettingsRow(
            title = "What has been measured",
            subtitle = "See which songs Wanda can recognise and hum-search, and which are still " +
                "waiting.",
            onClick = actions.onOpenFingerprints
        )
    }

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
