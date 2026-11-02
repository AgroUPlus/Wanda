package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** The Library's title, with the way into History where Home keeps Settings. */
@Composable
internal fun LibraryTitleRow(onOpenHistory: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        // History is a log rather than a collection, so it reads better as one thing you can go
        // and look at than as another tab competing with them.
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.library_listening_history))
        }
    }
}

/**
 * The section tabs.
 *
 * The expressive indicator, and the reason it is worth spelling out: the stock one is a bar
 * spanning the whole tab, which on a row of clipped labels read as a highlight on a column rather
 * than as a marker under a word. `matchContentSize` shrinks it to the label it belongs to, and a
 * rounded shape makes it a pill instead of a rule — so the selected tab is legible from its
 * silhouette before any text is read. Scrollable so each label stays whole however many sections
 * are shown.
 */
@Composable
internal fun LibraryTabRow(
    tabs: List<LibraryTab>,
    selectedPage: Int,
    onSelect: (LibraryTab) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedPage,
        edgePadding = 12.dp,
        divider = {},
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedPage, matchContentSize = true),
                width = Dp.Unspecified,
                height = 4.dp,
                shape = MaterialTheme.shapes.large
            )
        }
    ) {
        tabs.forEachIndexed { index, entry ->
            val isSelected = index == selectedPage
            Tab(
                selected = isSelected,
                onClick = { onSelect(entry) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        text = stringResource(entry.label),
                        style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
