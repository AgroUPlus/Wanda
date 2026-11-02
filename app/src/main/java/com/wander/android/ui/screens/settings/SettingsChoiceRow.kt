package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.ConnectedToggleGroup

/**
 * A setting with a handful of mutually exclusive values, shown as one connected button row under
 * its title — the same shape as the loudness mode picker, so every "pick one" setting reads alike.
 *
 * For a short list only: every option is on screen at once, which is the point (no dialog to open
 * to find out what the choices are), and also the limit — past four or five short labels the
 * buttons stop fitting a phone's width.
 */
@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    subtitle: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ConnectedToggleGroup(
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            enabled = enabled
        )
    }
}
