package com.wander.android.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.wander.android.ui.screens.search.SearchLayer

/** A root tab with the dock's search opening over it — see [SearchLayer]. */
@Composable
internal fun NavHostController.TabWithSearch(
    contentPadding: PaddingValues,
    tab: @Composable () -> Unit
) = SearchLayer(
    contentPadding = contentPadding,
    onOpenAlbum = { navigateSettled(Routes.album(it)) },
    onOpenArtist = { name, id -> navigateSettled(Routes.artist(name, id)) },
    base = tab
)
