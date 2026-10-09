package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Replaces Home's greeting while the customizer is open. */
@Composable
internal fun HomeEditBar(onReset: () -> Unit, onDone: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.home_edit_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onReset) { Text(stringResource(R.string.home_edit_reset)) }
        Button(onClick = onDone) { Text(stringResource(R.string.home_edit_done)) }
    }
}
