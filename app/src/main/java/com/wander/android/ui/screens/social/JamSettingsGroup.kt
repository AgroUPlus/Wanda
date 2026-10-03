package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.wander.android.R
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape

/**
 * The host's two room switches, as one segmented group. The whole row toggles, not just the
 * switch, because the row is the target a thumb actually lands on.
 */
@Composable
internal fun JamSettingsGroup(
    isRadioEnabled: Boolean,
    onRadioChange: (Boolean) -> Unit,
    openToFriends: Boolean,
    onOpenToFriendsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        SettingRow(
            title = stringResource(R.string.social_jam_radio),
            subtitle = stringResource(R.string.social_auto_blends_room_s_music),
            checked = isRadioEnabled,
            onCheckedChange = onRadioChange,
            shape = segmentedShape(0, 2, outer = GroupOuter)
        )
        SettingRow(
            title = stringResource(R.string.social_open_friends),
            subtitle = stringResource(if (openToFriends) R.string.jam_open_to_friends_on else R.string.jam_open_to_friends_off),
            checked = openToFriends,
            onCheckedChange = onOpenToFriendsChange,
            shape = segmentedShape(1, 2, outer = GroupOuter)
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = colors.surfaceContainerHigh) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 1.35.em),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Null: the row above already handles the toggle, and a second target would read twice.
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedBorderColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                    uncheckedTrackColor = colors.surfaceContainerHighest,
                    uncheckedBorderColor = colors.outline,
                    uncheckedThumbColor = colors.outline
                )
            )
        }
    }
}

/** The settings group's outer corners: a step tighter than a list's, inside a card that is already round. */
private val GroupOuter = 20.dp

