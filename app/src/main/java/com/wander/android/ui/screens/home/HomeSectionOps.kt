package com.wander.android.ui.screens.home

import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.UnifiedTrack
import java.time.LocalTime

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
 * Home with the online feed folded in: Quick picks and Recently Played stay on top, the feed's
 * shelves follow them, then the rest, then "Because you listened to" built from [seed]'s radio.
 */
internal fun List<HomeSection>.withFeed(
    feed: List<RecommendedShelf>,
    seed: UnifiedTrack?,
    suggestions: List<UnifiedTrack>
): List<HomeSection> {
    val feedIds = feed.map { it.id }.toSet()
    return buildList {
        this@withFeed.find { it.id == SectionOnRepeat }?.let { add(it) }
        this@withFeed.find { it.id == SectionRecentlyPlayed }?.let { add(it) }
        feed.forEach { shelf -> add(carousel(shelf.id, shelf.title, shelf.tracks.take(CarouselSize))) }
        // The rest, without duplicating the feed or the "because" shelf rebuilt below.
        this@withFeed.filterNot {
            it.id == SectionOnRepeat || it.id == SectionRecentlyPlayed || it.id in feedIds || it.id == SectionBecause
        }.forEach { add(it) }
        if (seed != null && suggestions.isNotEmpty()) {
            add(
                shelf(
                    id = SectionBecause,
                    title = "Because you listened to ${seed.title}",
                    style = HomeSectionStyle.FEATURED_HERO,
                    tracks = suggestions
                        .filter { it.id != seed.id }
                        .distinctBy { it.title.lowercase() }
                        .filterNot { it.title.equals(seed.title, ignoreCase = true) }
                )
            )
        }
    }.filterNot(HomeSection::isEmpty)
}

/** Time of day the user is most likely reading this. */
internal fun greeting(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
