package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.playbackStorageTab(
    offline: Boolean,
    onOfflineChange: (Boolean) -> Unit,
    preloadNext: Boolean,
    onPreloadNextChange: (Boolean) -> Unit,
    cacheBytes: Long,
    onDownloadLiked: () -> Unit,
    onIndexFingerprints: () -> Unit,
    onOpenFingerprints: () -> Unit,
    indexOnMobileData: Boolean,
    onIndexOnMobileDataChange: (Boolean) -> Unit,
    measuringPaused: Boolean,
    onMeasuringPausedChange: (Boolean) -> Unit,
    downloadingPaused: Boolean,
    onDownloadingPausedChange: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    item(key = "offline") {
        SettingsToggle(
            title = "Offline mode",
            // Worth saying, because the app now flips this for you if you agree: without the
            // second sentence the toggle looks like it moved on its own.
            subtitle = "Only play what is already on this device. " +
                "Wanda offers to turn this on and off as your connection changes.",
            checked = offline,
            onCheckedChange = onOfflineChange
        )
    }

    item(key = "preload_next") {
        SettingsToggle(
            title = "Ready the next track",
            // Said plainly, because it is a real cost and the honest reason to turn it off.
            subtitle = "Fetch the first couple of seconds ahead of time so skipping starts " +
                "instantly. Uses a little data on a track you might not play.",
            checked = preloadNext,
            onCheckedChange = onPreloadNextChange
        )
    }

    item(key = "index_fingerprints") {
        SettingsRow(
            title = "Measure the library now",
            // Named for what it produces rather than for the machinery. "Fingerprint" means
            // nothing to most people; recognising a song and building a radio are the results.
            subtitle = "Lets Wanda recognise your songs and build radios from how they sound. " +
                "Otherwise this happens on Wi-Fi while charging.",
            onClick = onIndexFingerprints
        )
    }

    item(key = "index_on_mobile_data") {
        SettingsToggle(
            title = "Measure over mobile data",
            // The cost stated in the units it is actually paid in. "Uses data" is not something
            // anyone can weigh; "a minute per song" is.
            subtitle = "Measuring a streamed song reads about a minute of it. On Wi-Fi that is " +
                "free, which is why this is off by default.",
            checked = indexOnMobileData,
            onCheckedChange = onIndexOnMobileDataChange
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
            title = if (measuringPaused) "Resume measuring" else "Pause measuring",
            subtitle = if (measuringPaused) {
                "Measuring is paused. Resuming starts a pass now and retries anything that failed."
            } else {
                "Stops the current pass and keeps it stopped until you resume. " +
                    "Sync and downloads are unaffected."
            },
            onClick = { onMeasuringPausedChange(!measuringPaused) }
        )
    }

    item(key = "pause_downloads") {
        SettingsRow(
            title = if (downloadingPaused) "Resume downloads" else "Pause downloads",
            subtitle = if (downloadingPaused) {
                "Downloading liked tracks is paused."
            } else {
                "Stops downloading liked tracks until you resume."
            },
            onClick = { onDownloadingPausedChange(!downloadingPaused) }
        )
    }

    item(key = "view_fingerprints") {
        SettingsRow(
            title = "What has been measured",
            subtitle = "See which songs Wanda can recognise and hum-search, and which are still " +
                "waiting.",
            onClick = onOpenFingerprints
        )
    }

    item(key = "download") {
        SettingsRow(
            title = "Download liked tracks now",
            subtitle = "Otherwise this happens on Wi-Fi while charging",
            onClick = onDownloadLiked
        )
    }

    item(key = "cache") {
        SettingsRow(
            title = "Clear streaming cache",
            subtitle = formatBytes(cacheBytes) + " in use",
            onClick = onClearCache
        )
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "0 MB"
}
