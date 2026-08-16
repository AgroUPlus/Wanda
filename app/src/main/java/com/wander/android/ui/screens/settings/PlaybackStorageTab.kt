package com.wander.android.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.playbackStorageTab(
    offline: Boolean,
    onOfflineChange: (Boolean) -> Unit,
    cacheBytes: Long,
    onDownloadLiked: () -> Unit,
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
