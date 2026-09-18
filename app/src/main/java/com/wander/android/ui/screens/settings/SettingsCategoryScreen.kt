package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * One page of settings — the rows that used to be one tab of the pager.
 *
 * The bodies are the same `LazyListScope` extensions they always were, unchanged: a tab and a page
 * are the same list of rows, and the only thing that differs is how you arrive at it.
 *
 * The header takes the top inset and the list takes the bottom, as on every other screen with a
 * fixed header over a scroll — see `ScreenInsets`. Doing it here rather than in each page body is
 * the point: a rule each new page had to remember is a rule that gets forgotten, and this app has
 * shipped a back arrow behind the clock before.
 *
 * The title lives in a real [LargeTopAppBar], not a hand-rolled shrinking `Text` — M3's own
 * component already gets the behaviour right: the title sits on its own row *under* the back
 * arrow while expanded, and only folds up onto the arrow's row once the list has scrolled past it.
 * [TopAppBarDefaults.windowInsets] is overridden to empty because this app's Scaffold already
 * accounts for the status bar via [headerInset] — letting the bar apply its own would double it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsCategoryScreen(
    category: SettingsCategory,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenMergePreview: () -> Unit,
    onOpenFingerprints: () -> Unit
) {
    val host = rememberSettingsHost(
        onNavidromeLogin = onNavidromeLogin,
        onYouTubeLogin = onYouTubeLogin,
        onOpenImport = onOpenImport,
        onOpenMergePreview = onOpenMergePreview,
        onOpenFingerprints = onOpenFingerprints
    )

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
        LargeTopAppBar(
            title = { Text(stringResource(category.label)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back_settings)
                    )
                }
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
            when (category) {
                SettingsCategory.CONNECTIONS -> connectionsTab(host.state, host.actions)
                SettingsCategory.SYNC -> syncTab(host.state, host.actions, host.devices)
                SettingsCategory.APPEARANCE -> appearanceTab(host.state, host.actions)
                SettingsCategory.PLAYBACK -> playbackStorageTab(host.state, host.actions)
                SettingsCategory.EXTERNAL -> externalTab(host.state, host.actions)
                SettingsCategory.PRIVACY -> privacyTab(host.state, host.actions)
                SettingsCategory.ABOUT -> aboutTab(host.state, host.actions)
            }
        }
    }
}
