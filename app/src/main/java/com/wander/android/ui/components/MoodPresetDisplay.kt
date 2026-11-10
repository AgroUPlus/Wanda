package com.wander.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset

/** The localized label for a [MoodPreset] — the one place its [MoodPreset.key] becomes text. */
@Composable
internal fun MoodPreset.label(): String = stringResource(
    when (key) {
        "late_night_chill" -> R.string.mood_preset_late_night_chill
        "focus_flow" -> R.string.mood_preset_focus_flow
        "morning_energizer" -> R.string.mood_preset_morning_energizer
        "high_voltage" -> R.string.mood_preset_high_voltage
        else -> R.string.mood_preset_late_night_chill
    }
)

/** One glyph per preset, standing in for its tempo/energy at a glance. */
internal fun MoodPreset.icon(): ImageVector = when (key) {
    "late_night_chill" -> Icons.Rounded.Nightlight
    "focus_flow" -> Icons.Rounded.CenterFocusStrong
    "morning_energizer" -> Icons.Rounded.WbSunny
    "high_voltage" -> Icons.Rounded.Bolt
    else -> Icons.Rounded.Radio
}
