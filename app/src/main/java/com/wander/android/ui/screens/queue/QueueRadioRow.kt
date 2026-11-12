package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * What happens after the last upcoming track, in one line, with the switch that decides it.
 *
 * Radio mode was only reachable by long-pressing the player's queue button — persisted, and
 * invisible until then. Here it sits where the question comes up: at the end of the queue.
 *
 * @param artist who radio would carry on from: the artist playing now.
 */
@Composable
internal fun QueueRadioRow(
    isRadioMode: Boolean,
    artist: String?,
    onRadioModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.queue_next_radio_switch)
    val outcome = when {
        !isRadioMode -> stringResource(R.string.queue_next_then_silence)
        artist.isNullOrBlank() -> stringResource(R.string.queue_next_then_radio)
        else -> stringResource(R.string.queue_next_then_radio_from, artist)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = isRadioMode, role = Role.Switch, onValueChange = onRadioModeChange)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isRadioMode) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isRadioMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        // The row is the control; the switch only shows its state, so it takes no clicks of its own.
        Switch(checked = isRadioMode, onCheckedChange = null)
    }
}
