package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Genres from the library that do not have a shelf yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddShelfSheet(genres: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.home_shelf_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (genres.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_shelf_add_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                LazyColumn {
                    items(genres, key = { it }) { genre ->
                        ListItem(
                            headlineContent = { Text(genre) },
                            modifier = Modifier.clickable { onAdd(genre) }
                        )
                    }
                }
            }
        }
    }
}
