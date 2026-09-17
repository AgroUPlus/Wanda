package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.rememberShelfEntranceScale

internal fun LazyListScope.playbackStorageTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    var i = 0

    val secPlaybackIndex = i++
    item(key = "sec_playback") {
        SettingsSection("Playback", modifier = Modifier.scale(rememberShelfEntranceScale(secPlaybackIndex)))
    }

    val offlineIndex = i++
    item(key = "offline") {
        SettingsToggle(
            title = stringResource(R.string.settings_offline_mode),
            // Worth saying, because the app now flips this for you if you agree: without the
            // second sentence the toggle looks like it moved on its own.
            subtitle = stringResource(R.string.settings_only_play_what_already_device),
            checked = state.offline,
            onCheckedChange = actions.onOfflineChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(offlineIndex))
        )
    }

    val preloadNextIndex = i++
    item(key = "preload_next") {
        SettingsToggle(
            title = stringResource(R.string.settings_ready_next_track),
            // Said plainly, because it is a real cost and the honest reason to turn it off.
            subtitle = stringResource(R.string.settings_fetch_first_couple_seconds_ahead),
            checked = state.preloadNext,
            onCheckedChange = actions.onPreloadNextChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(preloadNextIndex))
        )
    }

    val secMeasuringIndex = i++
    item(key = "sec_measuring") {
        SettingsSection("Measuring", modifier = Modifier.scale(rememberShelfEntranceScale(secMeasuringIndex)))
    }

    val indexFingerprintsIndex = i++
    item(key = "index_fingerprints") {
        SettingsRow(
            title = stringResource(R.string.settings_measure_library_now),
            // Named for what it produces rather than for the machinery. "Fingerprint" means
            // nothing to most people; recognising a song and building a radio are the results.
            subtitle = stringResource(R.string.settings_lets_wanda_recognise_songs_build),
            onClick = actions.onIndexFingerprints,
            modifier = Modifier.scale(rememberShelfEntranceScale(indexFingerprintsIndex))
        )
    }

    val indexOnMobileDataIndex = i++
    item(key = "index_on_mobile_data") {
        SettingsToggle(
            title = stringResource(R.string.settings_measure_over_mobile_data),
            // The cost stated in the units it is actually paid in. "Uses data" is not something
            // anyone can weigh; "a minute per song" is.
            subtitle = stringResource(R.string.settings_measuring_streamed_song_reads_about),
            checked = state.indexOnMobileData,
            onCheckedChange = actions.onIndexOnMobileDataChange,
            modifier = Modifier.scale(rememberShelfEntranceScale(indexOnMobileDataIndex))
        )
    }

    val viewFingerprintsIndex = i++
    item(key = "view_fingerprints") {
        SettingsRow(
            title = stringResource(R.string.settings_what_has_been_measured),
            // Pause/resume of a running pass lives on the progress notification, not here: it is
            // an action on work in flight, and the notification is where that work is already
            // visible. This screen is the report; that one is the remote control.
            subtitle = stringResource(R.string.settings_see_which_songs_wanda_can),
            onClick = actions.onOpenFingerprints,
            modifier = Modifier.scale(rememberShelfEntranceScale(viewFingerprintsIndex))
        )
    }

    val secStorageIndex = i++
    item(key = "sec_storage") {
        SettingsSection("Storage", modifier = Modifier.scale(rememberShelfEntranceScale(secStorageIndex)))
    }

    val downloadIndex = i++
    item(key = "download") {
        SettingsRow(
            title = stringResource(R.string.settings_download_liked_tracks_now),
            subtitle = stringResource(R.string.settings_otherwise_happens_wi_fi_while),
            onClick = actions.onDownloadLiked,
            modifier = Modifier.scale(rememberShelfEntranceScale(downloadIndex))
        )
    }

    val cacheIndex = i++
    item(key = "cache") {
        SettingsRow(
            title = stringResource(R.string.settings_clear_streaming_cache),
            subtitle = formatBytes(state.cacheBytes) + " in use",
            onClick = actions.onClearCache,
            modifier = Modifier.scale(rememberShelfEntranceScale(cacheIndex))
        )
    }

    val storageButlerIndex = i++
    item(key = "storage_butler") {
        StorageButlerSection(modifier = Modifier.scale(rememberShelfEntranceScale(storageButlerIndex)))
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "0 MB"
}
