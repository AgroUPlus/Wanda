package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard

/** The field at the top of the settings hub. Filters [SettingsSearchIndex] as it is typed into. */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * The rows matching [query], each naming the page it is on, in place of the category list.
 *
 * Resolved through the context rather than `stringResource` per entry because the match has to
 * read every label, including the ones it then drops, and that is not composition's business.
 */
internal fun LazyListScope.settingsSearchResults(
    query: String,
    onOpen: (SettingsSearchEntry) -> Unit
) {
    item(key = "search_results") {
        val context = LocalContext.current
        val results = searchSettings(query, context::getString)
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        } else {
            val rows: List<@Composable () -> Unit> = results.map { entry ->
                {
                    SettingsRow(
                        title = stringResource(entry.label),
                        subtitle = stringResource(entry.category.label),
                        onClick = { onOpen(entry) },
                        icon = entry.category.icon
                    )
                }
            }
            GroupedCard(items = rows)
        }
    }
}
