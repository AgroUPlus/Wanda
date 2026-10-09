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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.ExpressiveButton

/**
 * Replaces Home's greeting while the customizer is open: a title, then Reset and Done side by side,
 * then a big Add shelf. Reset asks first; it undoes every edit.
 */
@Composable
internal fun HomeEditBar(
    onReset: () -> Unit,
    onDone: () -> Unit,
    onAddShelf: () -> Unit,
    suggestion: String?,
    modifier: Modifier = Modifier
) {
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.home_edit_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.home_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            AnimatedVisibility(
                visible = suggestion != null,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
            ) {
                Text(
                    text = suggestion.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveButton(
                text = stringResource(R.string.home_edit_reset),
                onClick = { confirmingReset = true },
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface,
                icon = Icons.Rounded.Restore,
                modifier = Modifier.weight(1f)
            )
            ExpressiveButton(
                text = stringResource(R.string.home_edit_done),
                onClick = onDone,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f)
            )
        }

        ExpressiveButton(
            text = stringResource(R.string.home_shelf_add),
            onClick = onAddShelf,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Rounded.Add,
            height = 72.dp,
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
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
