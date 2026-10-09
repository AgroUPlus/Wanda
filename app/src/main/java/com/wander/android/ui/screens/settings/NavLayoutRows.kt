package com.wander.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.navigation.DockItem
import com.wander.android.ui.navigation.NavLayoutViewModel
import com.wander.android.ui.screens.library.LibraryTab

/** Which shortcuts sit beside the search field, and in what order. */
@Composable
internal fun DockLayoutRow() {
    val viewModel = hiltViewModel<NavLayoutViewModel>()
    val items by viewModel.dockItems.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }

    val names = items.map { stringResource(it.label) }
    SettingsRow(
        title = stringResource(R.string.settings_dock_title),
        subtitle = names.joinToString().ifEmpty { stringResource(R.string.settings_dock_none) },
        onClick = { open = true },
        icon = Icons.Rounded.Apps
    )
    if (open) {
        OrderedChoiceSheet(
            title = stringResource(R.string.settings_dock_title),
            hint = stringResource(R.string.settings_dock_hint, DockItem.MaxShown),
            all = DockItem.entries.map { Choice(it, stringResource(it.label), it.icon) },
            selected = items,
            minSelected = 0,
            maxSelected = DockItem.MaxShown,
            onChange = viewModel::saveDockItems,
            onDismiss = { open = false }
        )
    }
}

/** Which sections the Library shows, and in what order. */
@Composable
internal fun LibrarySectionsRow() {
    val viewModel = hiltViewModel<NavLayoutViewModel>()
    val tabs by viewModel.libraryTabs.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }

    val names = tabs.map { stringResource(it.label) }
    SettingsRow(
        title = stringResource(R.string.settings_library_sections),
        subtitle = names.joinToString(),
        onClick = { open = true },
        icon = Icons.Rounded.LibraryMusic
    )
    if (open) {
        OrderedChoiceSheet(
            title = stringResource(R.string.settings_library_sections),
            hint = stringResource(R.string.settings_library_sections_hint),
            all = LibraryTab.entries.map { Choice(it, stringResource(it.label), it.icon()) },
            selected = tabs,
            minSelected = 1,
            maxSelected = LibraryTab.entries.size,
            onChange = viewModel::saveLibraryTabs,
            onDismiss = { open = false }
        )
    }
}

private fun LibraryTab.icon(): ImageVector = when (this) {
    LibraryTab.LIKED -> Icons.Rounded.Favorite
    LibraryTab.TRACKS -> Icons.Rounded.MusicNote
    LibraryTab.ALBUMS -> Icons.Rounded.Album
    LibraryTab.PLAYLISTS -> Icons.Rounded.QueueMusic
    LibraryTab.PODCASTS -> Icons.Rounded.Podcasts
    LibraryTab.DOWNLOADS -> Icons.Rounded.DownloadForOffline
}
