package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeSectionStyle
import com.wander.android.ui.screens.home.layout.ShelfConfig

/** null = the shelf's own default. */
private val StyleChoices = listOf(
    null,
    HomeSectionStyle.TRACK_CAROUSEL,
    HomeSectionStyle.TRACK_LIST,
    HomeSectionStyle.LARGE_GRID,
    HomeSectionStyle.FEATURED_HERO
)
private val CountChoices = listOf(null, 6, 12, 20)

/** How a shelf looks and how long it is. The preview behind the sheet updates as you pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelfSettingsSheet(
    section: HomeSection,
    config: ShelfConfig,
    onStyle: (HomeSectionStyle?) -> Unit,
    onCount: (Int?) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(section.title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_shelf_layout), style = MaterialTheme.typography.labelLarge)
            ConnectedToggleGroup(
                options = StyleChoices,
                selected = config.style,
                label = { it.label() },
                onSelect = onStyle
            )
            Text(stringResource(R.string.home_shelf_length), style = MaterialTheme.typography.labelLarge)
            ConnectedToggleGroup(
                options = CountChoices,
                selected = config.count,
                label = { count -> count?.toString() ?: stringResource(R.string.home_shelf_default) },
                onSelect = onCount
            )
            onRemove?.let { remove ->
                TextButton(onClick = remove) { Text(stringResource(R.string.home_shelf_remove)) }
            }
        }
    }
}

@Composable
private fun HomeSectionStyle?.label(): String = stringResource(
    when (this) {
        HomeSectionStyle.TRACK_CAROUSEL -> R.string.home_style_carousel
        HomeSectionStyle.TRACK_LIST -> R.string.home_style_list
        HomeSectionStyle.LARGE_GRID -> R.string.home_style_grid
        HomeSectionStyle.FEATURED_HERO -> R.string.home_style_hero
        else -> R.string.home_shelf_default
    }
)
