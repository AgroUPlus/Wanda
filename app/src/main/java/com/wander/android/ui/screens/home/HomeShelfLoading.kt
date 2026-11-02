package com.wander.android.ui.screens.home

import android.content.Context
import com.wander.android.R
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.HomeShelfRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// The two phases of a Home load, used by [HomeViewModel.refresh].

/**
 * Phase 1: the library's own shelves, read straight from Room so the screen paints at once.
 *
 * [sources] are the configured backends the lead shelf is built across; [continueListening] is
 * the episodes shelf, which is already held and only slotted in. [context] names the shelves.
 */
internal suspend fun HomeShelfRepository.localShelves(
    sources: List<SourceType>,
    continueListening: HomeSection,
    context: Context
): List<HomeSection> = coroutineScope {
    val onRepeat = async { getQuickPicks(CarouselSize, sources) }
    val recentlyPlayed = async { getRecentlyPlayed(CarouselSize) }
    val liked = async { getLikedTracks(CarouselSize) }
    val discover = async { getNeverPlayed(CarouselSize) }

    buildList {
        // The lead shelf earns legible full-width rows; the second earns big
        // artwork. The rest stay carousels, so the top of Home has a shape to it.
        add(shelf(SectionOnRepeat, context.getString(R.string.home_quick_picks), HomeSectionStyle.TRACK_PAGER, onRepeat.await()))
        add(
            shelf(SectionRecentlyPlayed, context.getString(R.string.home_recently_played), HomeSectionStyle.HERO_CAROUSEL, recentlyPlayed.await())
        )
        add(continueListening)
        add(shelf(SectionLiked, context.getString(R.string.home_your_favorites), HomeSectionStyle.HERO_CAROUSEL, liked.await()))
        add(shelf(SectionDiscover, context.getString(R.string.home_discover), HomeSectionStyle.DISCOVER_MASONRY, discover.await()))
    }.filterNot(HomeSection::isEmpty)
}

/** What phase 2 folds into Home: the online feed, and the radio of the last track played. */
internal data class OnlineFeed(
    val feed: List<RecommendedShelf>,
    val seed: UnifiedTrack?,
    val suggestions: List<UnifiedTrack>
)

/** Phase 2: the online feed and the "because you listened to" radio, fetched side by side. */
internal suspend fun loadOnlineFeed(
    recommendationRepository: RecommendationRepository,
    homeShelfRepository: HomeShelfRepository,
    musicRepository: MusicRepository
): OnlineFeed = coroutineScope {
    val feedDeferred = async { recommendationRepository.getShelves() }
    val recommendedDeferred = async {
        val seed = homeShelfRepository.getRecentlyPlayed(1).firstOrNull()
        seed to seed?.let { musicRepository.generateRadio(it, CarouselSize) }.orEmpty()
    }
    // Only the shelves that are a kind of music; see `RecommendedShelf.isGeneric`.
    val feed = feedDeferred.await().filter { it.isGeneric }
    val (seed, suggestions) = recommendedDeferred.await()
    OnlineFeed(feed, seed, suggestions)
}
