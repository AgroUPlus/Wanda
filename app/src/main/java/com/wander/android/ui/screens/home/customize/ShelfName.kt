package com.wander.android.ui.screens.home.customize

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.screens.home.ExtraShelf
import com.wander.android.ui.screens.home.GenreShelfPrefix
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.genreShelfTitle
import com.wander.android.ui.screens.home.layout.ShelfConfig

/**
 * What to call a shelf in the customizer. A shelf with tracks carries its own title; an empty one
 * has none, so it is named from its settings.
 */
@Composable
internal fun shelfName(section: HomeSection, config: ShelfConfig): String = when {
    section.title.isNotEmpty() -> section.title
    config.id.startsWith(GenreShelfPrefix) ->
        genreShelfTitle(config.categories, stringResource(R.string.shelf_christian), stringResource(R.string.shelf_genre))
    else -> ExtraShelf.of(config.id)?.let { stringResource(it.title) }.orEmpty()
}
