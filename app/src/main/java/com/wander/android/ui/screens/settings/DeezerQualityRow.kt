package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.deezer.DeezerAudioFormat

/**
 * The Deezer quality setting, as stored for `DeezerAudioFormat.ladderFrom`.
 *
 * Each choice is a ceiling, not an exact pick: the account's plan still caps it, and a track with
 * no file at the chosen quality plays at the next one down. [AUTO] sets no ceiling at all.
 */
internal enum class DeezerQualityChoice(val tag: String, @StringRes val label: Int) {
    AUTO("AUTO", R.string.settings_deezer_quality_auto),
    FLAC(DeezerAudioFormat.FLAC.tag, R.string.settings_deezer_quality_flac),
    MP3_320(DeezerAudioFormat.MP3_320.tag, R.string.settings_quality_320_kbps),
    MP3_128(DeezerAudioFormat.MP3_128.tag, R.string.settings_quality_128_kbps);

    companion object {
        /** The stored choice; anything else (never set, or cleared at sign-out) is [AUTO]. */
        fun fromTag(tag: String): DeezerQualityChoice = entries.firstOrNull { it.tag == tag } ?: AUTO
    }
}

/** Shown only while Deezer is signed in: with no account there is nothing to stream. */
@Composable
internal fun DeezerQualityRow(
    modifier: Modifier = Modifier,
    viewModel: StreamQualityViewModel = hiltViewModel()
) {
    val selected by viewModel.deezerQuality.collectAsStateWithLifecycle()
    SettingsChoiceRow(
        modifier = modifier,
        title = stringResource(R.string.settings_deezer_quality),
        subtitle = stringResource(R.string.settings_deezer_quality_sub),
        options = DeezerQualityChoice.entries,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = viewModel::setDeezerQuality
    )
}
