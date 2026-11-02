package com.wander.android.ui.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Laid over a selected row's cover: a tick on the primary colour, so the selection reads at a
 * glance down a long list, not only from the row's tint.
 */
@Composable
internal fun SelectedArtworkMark(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), MaterialTheme.shapes.medium)
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = stringResource(R.string.selection_selected),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
