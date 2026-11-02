package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.navidrome.NavidromeStreamQuality

/**
 * One of Navidrome's two quality settings: on Wi-Fi ([metered] false), or on mobile data.
 *
 * Which applies is decided per track, when its stream is asked for, from whether the network in
 * use then is metered — so a song started on Wi-Fi keeps its quality if the phone walks out of
 * range mid-song, and the next one follows the new network.
 */
@Composable
internal fun NavidromeQualityRow(
    metered: Boolean,
    modifier: Modifier = Modifier,
    viewModel: StreamQualityViewModel = hiltViewModel()
) {
    val flow = if (metered) viewModel.navidromeQualityMobile else viewModel.navidromeQualityWifi
    val selected by flow.collectAsStateWithLifecycle()
    SettingsChoiceRow(
        modifier = modifier,
        title = stringResource(
            if (metered) R.string.settings_navidrome_quality_mobile else R.string.settings_navidrome_quality_wifi
        ),
        // The seeking caveat is stated here because it is the real cost of converting: a stream
        // the server makes as it sends it has no byte at an arbitrary offset to jump to.
        subtitle = stringResource(R.string.settings_navidrome_quality_sub),
        options = NavidromeStreamQuality.entries,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = if (metered) viewModel::setNavidromeQualityMobile else viewModel::setNavidromeQualityWifi
    )
}

@get:StringRes
private val NavidromeStreamQuality.label: Int
    get() = when (this) {
        NavidromeStreamQuality.ORIGINAL -> R.string.settings_navidrome_quality_original
        NavidromeStreamQuality.KBPS_320 -> R.string.settings_quality_320_kbps
        NavidromeStreamQuality.KBPS_192 -> R.string.settings_navidrome_quality_192
        NavidromeStreamQuality.KBPS_128 -> R.string.settings_quality_128_kbps
    }
