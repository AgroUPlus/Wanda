package com.wander.android.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R

/** The ⋮ at the end of a Library row, opening the sheet its long press opens. */
@Composable
internal fun LibraryMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.common_more_options),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
