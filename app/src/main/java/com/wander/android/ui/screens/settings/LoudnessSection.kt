package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.security.SecureLoudnessPreferences
import com.wander.android.data.model.LoudnessMode
import com.wander.android.ui.components.ConnectedToggleGroup
import kotlin.math.roundToInt

/**
 * Loudness normalisation: which ReplayGain value evens out playback, and a pre-amp.
 *
 * The pre-amp is committed when the thumb is let go rather than on every step of a drag, so a
 * drag is one write to encrypted storage, not a dozen.
 */
@Composable
internal fun LoudnessSection(
    modifier: Modifier = Modifier,
    viewModel: LoudnessViewModel = hiltViewModel()
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val preampDb by viewModel.preampDb.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_loudness_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_loudness_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ConnectedToggleGroup(
            options = LoudnessMode.entries,
            selected = mode,
            label = { stringResource(it.label) },
            onSelect = viewModel::setMode
        )
        Text(
            text = stringResource(mode.hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (mode != LoudnessMode.OFF) PreampSlider(preampDb, viewModel::setPreampDb)
    }
}

@Composable
private fun PreampSlider(preampDb: Int, onCommit: (Int) -> Unit) {
    val range = SecureLoudnessPreferences.PREAMP_RANGE_DB
    var dragged by remember(preampDb) { mutableFloatStateOf(preampDb.toFloat()) }
    Column {
        Text(
            text = stringResource(R.string.settings_loudness_preamp, dragged.roundToInt()),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onCommit(dragged.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
        Text(
            text = stringResource(R.string.settings_loudness_preamp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@get:StringRes
private val LoudnessMode.label: Int
    get() = when (this) {
        LoudnessMode.OFF -> R.string.settings_loudness_mode_off
        LoudnessMode.TRACK -> R.string.settings_loudness_mode_track
        LoudnessMode.ALBUM -> R.string.settings_loudness_mode_album
        LoudnessMode.AUTO -> R.string.settings_loudness_mode_auto
    }

@get:StringRes
private val LoudnessMode.hint: Int
    get() = when (this) {
        LoudnessMode.OFF -> R.string.settings_loudness_hint_off
        LoudnessMode.TRACK -> R.string.settings_loudness_hint_track
        LoudnessMode.ALBUM -> R.string.settings_loudness_hint_album
        LoudnessMode.AUTO -> R.string.settings_loudness_hint_auto
    }
