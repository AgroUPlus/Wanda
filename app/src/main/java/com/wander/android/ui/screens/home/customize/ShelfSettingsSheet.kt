package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.screens.home.GenreShelfPrefix
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeSectionStyle
import com.wander.android.ui.screens.home.layout.ShelfConfig

private val CountChoices = listOf(null, 4, 6, 8)

/**
 * How a shelf looks and how long it is, and the only way to take it off Home. The shelf behind the
 * sheet changes as you pick. Mix shelves only draw mix cards, so they get just the remove button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShelfSettingsSheet(
    section: HomeSection,
    config: ShelfConfig,
    onStyle: (HomeSectionStyle?) -> Unit,
    onCount: (Int?) -> Unit,
    libraryGenres: List<String>,
    onCategory: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(shelfName(section, config), style = MaterialTheme.typography.titleLarge)
            if (config.id.startsWith(GenreShelfPrefix)) {
                ShelfCategoriesPicker(config, libraryGenres, onCategory, onLanguage)
            }
            if (section.mixes.isEmpty()) {
                Text(stringResource(R.string.home_shelf_layout), style = MaterialTheme.typography.labelLarge)
                ShelfStylePicker(selected = section.style, onSelect = onStyle)
                if (config.style != null) {
                    TextButton(onClick = { onStyle(null) }) { Text(stringResource(R.string.home_style_use_default)) }
                }
                Text(stringResource(R.string.home_shelf_length), style = MaterialTheme.typography.labelLarge)
                ConnectedToggleGroup(
                    options = CountChoices,
                    selected = config.count,
                    label = { count -> count?.toString() ?: stringResource(R.string.home_shelf_default) },
                    onSelect = onCount
                )
            }
            FilledTonalButton(
                onClick = onRemove,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.home_shelf_remove), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}
