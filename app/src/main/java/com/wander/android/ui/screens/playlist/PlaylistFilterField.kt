package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Playlists shorter than this are read at a glance and get no filter field. */
internal const val PlaylistFilterMinTracks = 20

/**
 * Narrows a long playlist to the songs whose title, artist or album match — on this device only,
 * over the tracks already loaded. While it holds a query the rows cannot be dragged: a drop
 * between two rows of a filtered list has no single place in the whole one.
 */
@Composable
internal fun PlaylistFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
    /** Shown under the field when the query matches nothing. */
    noMatches: Boolean
) {
    val focus = LocalFocusManager.current
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.playlist_filter_clear))
                    }
                }
            } else {
                null
            },
            label = { Text(stringResource(R.string.playlist_filter_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (noMatches) {
            Text(
                text = stringResource(R.string.playlist_filter_no_matches, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )
        }
    }
}
