package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberShelfEntranceScale

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
 *
 * The title reads the same as Home's own greeting — `headlineLarge`, transparent behind it — rather
 * than [LargeTopAppBar]'s own smaller default title style and opaque container: this *is* the
 * screen's greeting, not a bar sitting over it. It still collapses on scroll, because the row of
 * seven categories genuinely can run past the fold on a compact phone.
 *
 * Starts already collapsed — title small, same row it would share with a back arrow on any other
 * screen — rather than expanded like a fresh [LargeTopAppBar] normally would. `initialHeightOffset`
 * is clamped to the real (negative) height-offset limit once the bar measures itself, so this reads
 * as "already scrolled up" from the first frame while the scroll-driven expand/collapse this screen
 * needs still works exactly as before — the same large-title behaviour Android's own Settings app
 * has, just not defaulting to its expanded state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenCategory: (SettingsCategory) -> Unit
) {
    val listState = rememberLazyListState()
    val topBarState = rememberTopAppBarState(initialHeightOffset = -Float.MAX_VALUE)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
        LargeTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineLarge
                )
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            scrollBehavior = scrollBehavior
        )

        LazyColumn(
            state = listState,
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            item(key = "categories") {
                val categoryItems: List<@Composable () -> Unit> =
                    SettingsCategory.entries.mapIndexed { index, category ->
                        {
                            SettingsCategoryRow(
                                category = category,
                                onClick = { onOpenCategory(category) },
                                modifier = Modifier.scale(rememberShelfEntranceScale(index))
                            )
                        }
                    }
                GroupedCard(items = categoryItems)
            }
        }
    }
}
