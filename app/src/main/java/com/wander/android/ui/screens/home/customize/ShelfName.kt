package com.wander.android.ui.screens.home.customize

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.repository.ServiceProblem
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

/** Why a shelf has nothing to show, and what to do about it. */
@Composable
internal fun emptyShelfMessage(config: ShelfConfig, problem: ServiceProblem?): String = when {
    problem != null -> stringResource(problem.message())
    config.id == ExtraShelf.FRIENDS.id -> stringResource(R.string.home_shelf_empty_friends)
    config.id.startsWith(GenreShelfPrefix) && config.categories.isEmpty() -> stringResource(R.string.home_shelf_empty_categories)
    else -> stringResource(R.string.home_shelf_empty)
}

private fun ServiceProblem.message(): Int = when (this) {
    ServiceProblem.AGRO_NOT_PAIRED -> R.string.problem_agro_not_paired
    ServiceProblem.AGRO_UNREACHABLE -> R.string.problem_agro_unreachable
    ServiceProblem.AGRO_NO_DATA -> R.string.problem_agro_no_data
    ServiceProblem.AGRO_NOTHING_IN_LIBRARY -> R.string.problem_agro_nothing_in_library
    ServiceProblem.YOUTUBE_NOT_CONNECTED -> R.string.problem_youtube_not_connected
    ServiceProblem.YOUTUBE_UNREACHABLE -> R.string.problem_youtube_unreachable
    ServiceProblem.YOUTUBE_NO_SHELVES -> R.string.problem_youtube_no_shelves
}
