package com.wander.android.ui.screens.home

import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.HomeShelfRepository
import com.wander.android.ui.screens.home.layout.ShelfConfig

// Pure list operations on Home's shelves, shared by [HomeViewModel].

internal fun shelf(
    id: String,
    title: String,
    style: HomeSectionStyle,
    tracks: List<UnifiedTrack>
) = HomeSection(id = id, title = title, style = style, tracks = tracks)

internal fun carousel(id: String, title: String, tracks: List<UnifiedTrack>) =
    shelf(id, title, HomeSectionStyle.TRACK_CAROUSEL, tracks)

internal fun List<HomeSection>.withLikes(liked: Set<String>): List<HomeSection> = map { section ->
    section.copy(tracks = section.tracks.map { it.copy(isLiked = it.id in liked) })
}

/**
 * Replaces a shelf in place, adding it if it wasn't there and dropping it once it empties.
 *
 * Deliberately does **not** re-sort the whole list. It used to, by [SectionOrder] — which
 * silently rearranged Home the first time a like landed, because the recommendation shelves
 * and the per-source shelves are not in that list and all sorted to the end together. The
 * order the load built is the order Home keeps.
 */
internal fun List<HomeSection>.withSection(section: HomeSection): List<HomeSection> {
    val existing = indexOfFirst { it.id == section.id }
    if (existing >= 0) {
        return if (section.isEmpty) filterIndexed { index, _ -> index != existing }
        else toMutableList().also { it[existing] = section }
    }
    if (section.isEmpty) return this

    // New shelf: slot it in ahead of the first shelf it is meant to precede, so it does not
    // simply appear at the bottom of the screen.
    val rank = SectionOrder.indexOf(section.id).takeIf { it >= 0 } ?: return this + section
    val at = indexOfFirst { SectionOrder.indexOf(it.id) > rank }
    return if (at < 0) this + section else toMutableList().also { it.add(at, section) }
}

/**
 * The genre shelves the layout asks for that [have] does not hold yet. A genre with nothing in it
 * comes back absent, like any other empty shelf.
 */
internal suspend fun HomeShelfRepository.genreSections(
    layout: List<ShelfConfig>,
    have: Set<String> = emptySet()
): List<HomeSection> = layout
    .filter { it.id.startsWith(GenreShelfPrefix) && it.id !in have }
    .map { config ->
        val genre = config.id.removePrefix(GenreShelfPrefix)
        carousel(config.id, genre, getGenreTracks(genre, CarouselSize))
    }
    .filterNot(HomeSection::isEmpty)
