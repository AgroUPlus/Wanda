package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.ui.components.ConnectedToggleButtons
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.WandaSheet
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
    plays: Int?,
    sources: List<SourceType>,
    onSource: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    WandaSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(shelfName(section, config), style = MaterialTheme.typography.titleLarge)
            // Only once counting has begun; before that there is nothing true to say.
            plays?.let { PlaysPill(it) }
            if (config.id.startsWith(GenreShelfPrefix)) {
                ShelfCategoriesPicker(config, libraryGenres, onCategory, onLanguage)
            }
            if (section.mixes.isEmpty()) {
                Text(stringResource(R.string.home_shelf_layout), style = MaterialTheme.typography.labelLarge)
                ShelfStylePicker(selected = section.style, onSelect = onStyle)
                AnimatedVisibility(
                    visible = config.style != null,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                ) {
                    TextButton(onClick = { onStyle(null) }) { Text(stringResource(R.string.home_style_use_default)) }
                }
                // Only worth asking with more than one backend to choose between.
                if (sources.size > 1) {
                    Text(stringResource(R.string.home_shelf_sources), style = MaterialTheme.typography.labelLarge)
                    ConnectedToggleButtons(
                        options = sources,
                        // Nothing picked means every source.
                        isChecked = { it.name in config.sources },
                        role = Role.Checkbox,
                        label = { it.displayName },
                        onSelect = { onSource(it.name) },
                        equalWidth = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
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

/** How many songs were played from this shelf: a small pill, not a sentence. */
@Composable
private fun PlaysPill(plays: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = pluralStringResource(R.plurals.home_shelf_plays, plays, plays),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
