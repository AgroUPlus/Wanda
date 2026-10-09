package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.christian.ChristianArtists
import com.wander.android.data.christian.ChristianLanguage
import com.wander.android.ui.components.ConnectedToggleButtons
import com.wander.android.ui.screens.home.ChristianCategory
import com.wander.android.ui.screens.home.layout.ShelfConfig

/**
 * What a genre shelf is made of: Christian music, and any genre tagged in the library, in any
 * combination. Choosing Christian reveals the languages to draw its artists from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfCategoriesPicker(
    config: ShelfConfig,
    libraryGenres: List<String>,
    onCategory: (String) -> Unit,
    onLanguage: (String) -> Unit
) {
    Text(stringResource(R.string.home_shelf_categories), style = MaterialTheme.typography.labelLarge)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        (listOf(ChristianCategory) + libraryGenres).forEach { category ->
            FilterChip(
                selected = category in config.categories,
                onClick = { onCategory(category) },
                label = { Text(if (category == ChristianCategory) stringResource(R.string.shelf_christian) else category) }
            )
        }
    }
    if (ChristianCategory in config.categories) {
        Text(stringResource(R.string.home_shelf_languages), style = MaterialTheme.typography.labelLarge)
        ConnectedToggleButtons(
            options = ChristianArtists.languages,
            // No language picked means all of them.
            isChecked = { it.code in config.languages },
            role = Role.Checkbox,
            label = { it.label() },
            onSelect = { onLanguage(it.code) }
        )
    }
}

@Composable
private fun ChristianLanguage.label(): String = stringResource(
    when (this) {
        ChristianLanguage.PORTUGUESE -> R.string.language_portuguese
        ChristianLanguage.ENGLISH -> R.string.language_english
        ChristianLanguage.FRENCH -> R.string.language_french
    }
)
