package com.wander.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.security.SecureDownloadPreferences
import com.wander.android.ui.components.ConfirmDialog
import com.wander.android.ui.components.GroupedCard

private const val GIB = 1024L * 1024 * 1024

/** The download caps on offer, in bytes; [SecureDownloadPreferences.UNLIMITED] first. */
private val DownloadLimits = listOf(SecureDownloadPreferences.UNLIMITED, 1 * GIB, 2 * GIB, 5 * GIB, 10 * GIB)

/** The streaming cache sizes on offer, in bytes. */
private val StreamCacheSizes = listOf(GIB / 2, 1 * GIB, 2 * GIB, 4 * GIB)

/**
 * What downloads by itself, and how much of the phone it may use.
 *
 * Its own card rather than more rows in the storage one: these are standing rules for the
 * background sweep, where that card's rows are things to do now.
 */
@Composable
internal fun DownloadSettingsCard(
    modifier: Modifier = Modifier,
    viewModel: DownloadSettingsViewModel = hiltViewModel(),
    // The same instance the breakdown below this card reads, so it can be told the files are gone.
    butler: StorageButlerViewModel = hiltViewModel()
) {
    val liked by viewModel.autoDownloadLiked.collectAsStateWithLifecycle()
    val predicted by viewModel.autoDownloadPredicted.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val limit by viewModel.limitBytes.collectAsStateWithLifecycle()
    val deleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    GroupedCard(
        modifier = modifier,
        items = listOf<@Composable () -> Unit>(
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_auto_download_liked),
                    subtitle = stringResource(R.string.settings_auto_download_liked_sub),
                    checked = liked,
                    onCheckedChange = viewModel::setAutoDownloadLiked,
                    icon = Icons.Rounded.Favorite
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_auto_download_predicted),
                    subtitle = stringResource(R.string.settings_auto_download_predicted_sub),
                    checked = predicted,
                    onCheckedChange = viewModel::setAutoDownloadPredicted,
                    icon = Icons.Rounded.AutoAwesome
                )
            },
            {
                SettingsToggle(
                    title = stringResource(R.string.settings_download_wifi_only),
                    subtitle = stringResource(R.string.settings_download_wifi_only_sub),
                    checked = wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                    // Greyed rather than hidden: with nothing downloading by itself there is no
                    // network rule to set, but the rule is still there to find.
                    enabled = liked || predicted,
                    icon = Icons.Rounded.Wifi
                )
            },
            {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_download_limit),
                    subtitle = stringResource(R.string.settings_download_limit_sub),
                    options = DownloadLimits,
                    selected = limit.takeIf { it in DownloadLimits } ?: SecureDownloadPreferences.UNLIMITED,
                    label = { bytes -> sizeLabel(bytes) },
                    onSelect = viewModel::setLimitBytes
                )
            },
            {
                SettingsRow(
                    title = stringResource(R.string.settings_delete_all_downloads),
                    subtitle = stringResource(R.string.settings_delete_all_downloads_sub),
                    onClick = { confirmDelete = true },
                    destructive = true,
                    enabled = !deleting,
                    icon = Icons.Rounded.DeleteForever
                )
            }
        )
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all_downloads_confirm_title),
            message = stringResource(R.string.settings_delete_all_downloads_confirm_message),
            confirmLabel = stringResource(R.string.settings_delete_all_downloads_confirm),
            onConfirm = { viewModel.deleteAllDownloads(onDone = butler::refresh) },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** How big the streaming cache may grow. Applies from the next start; the subtitle says so. */
@Composable
internal fun StreamCacheSizeRow(
    modifier: Modifier = Modifier,
    viewModel: DownloadSettingsViewModel = hiltViewModel()
) {
    val size by viewModel.streamCacheBytes.collectAsStateWithLifecycle()
    SettingsChoiceRow(
        modifier = modifier,
        title = stringResource(R.string.settings_stream_cache_size),
        subtitle = stringResource(R.string.settings_stream_cache_size_sub),
        options = StreamCacheSizes,
        selected = size.takeIf { it in StreamCacheSizes } ?: SecureDownloadPreferences.DEFAULT_STREAM_CACHE_BYTES,
        label = { bytes -> sizeLabel(bytes) },
        onSelect = viewModel::setStreamCacheBytes
    )
}

/** Whole gigabytes as "N GB", half of one as "512 MB", and no cap as its own word. */
@Composable
private fun sizeLabel(bytes: Long): String = when {
    bytes <= SecureDownloadPreferences.UNLIMITED -> stringResource(R.string.settings_size_unlimited)
    bytes < GIB -> stringResource(R.string.settings_size_mb, (bytes / (1024 * 1024)).toInt())
    else -> stringResource(R.string.settings_size_gb, (bytes / GIB).toInt())
}
