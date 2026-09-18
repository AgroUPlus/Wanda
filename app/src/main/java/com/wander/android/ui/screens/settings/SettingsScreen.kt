package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.CollapsingTitle
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberCollapseFraction

/**
 * The settings hub: one list of places to go.
 *
 * This screen used to *be* the settings — seven tabs in a pager, every row of every section only a
 * swipe away and none of them findable. What it holds now is a title and seven rows, each with its
 * own colour and a sentence saying what is behind it; the rows themselves live on the pages this
 * opens. See [SettingsCategory] for the grouping and [SettingsCategoryScreen] for the pages.
 *
 * Deliberately free of plumbing. The ViewModel, the launchers and the confirmation dialogs are what
 * the *pages* need, and assembling them here would mean every visit to the settings paid for an
 * Agro refresh and a YouTube token check before drawing a list of seven labels — see
 * [rememberSettingsHost], which is where that now happens.
 */
@Composable
internal fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenCategory: (SettingsCategory) -> Unit
) {
    val listState = rememberLazyListState()
    val collapseFraction by rememberCollapseFraction(listState)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
        CollapsingTitle(
            text = stringResource(R.string.nav_settings),
            collapseFraction = collapseFraction
        )

        LazyColumn(
            state = listState,
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "categories") {
                GroupedCard {
                    SettingsCategory.entries.forEach { category ->
                        SettingsCategoryRow(
                            category = category,
                            onClick = { onOpenCategory(category) }
                        )
                    }
                }
            }
        }
    }
}
