package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Replaces Home's greeting while the customizer is open. Reset asks first; it undoes every edit. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeEditBar(onReset: () -> Unit, onDone: () -> Unit, suggestion: String?, modifier: Modifier = Modifier) {
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_edit_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { confirmingReset = true }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.home_edit_reset))
            }
            Button(onClick = onDone, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.home_edit_done), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
        Text(
            text = stringResource(R.string.home_edit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        AnimatedVisibility(
            visible = suggestion != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
        ) {
            Text(
                text = suggestion.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.home_edit_reset_title)) },
            text = { Text(stringResource(R.string.home_edit_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    onReset()
                }) { Text(stringResource(R.string.home_edit_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
