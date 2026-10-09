package com.wander.android.ui.screens.home

import android.content.Context
import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.data.christian.ChristianLanguage
import com.wander.android.data.christian.ChristianShelfRepository
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FriendPicksRepository
import com.wander.android.data.repository.HomeShelfRepository
import com.wander.android.ui.screens.home.layout.ShelfConfig

/** Shelves that are not on Home until the user adds them. [id] is what the saved layout stores. */
internal enum class ExtraShelf(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val style: HomeSectionStyle
) {
    REDISCOVER(ExtraShelfPrefix + "rediscover", R.string.shelf_rediscover, R.string.shelf_rediscover_summary, HomeSectionStyle.TRACK_CAROUSEL),
    HEAVY_ROTATION(ExtraShelfPrefix + "heavy_rotation", R.string.shelf_heavy_rotation, R.string.shelf_heavy_rotation_summary, HomeSectionStyle.HERO_CAROUSEL),
    FRESH(ExtraShelfPrefix + "fresh", R.string.shelf_fresh, R.string.shelf_fresh_summary, HomeSectionStyle.HERO_CAROUSEL),
    LATE_NIGHT(ExtraShelfPrefix + "late_night", R.string.shelf_late_night, R.string.shelf_late_night_summary, HomeSectionStyle.TRACK_CAROUSEL),
    RANDOM(ExtraShelfPrefix + "random", R.string.shelf_random, R.string.shelf_random_summary, HomeSectionStyle.DISCOVER_MASONRY),
    FRIENDS(ExtraShelfPrefix + "friends", R.string.shelf_friends, R.string.shelf_friends_summary, HomeSectionStyle.TRACK_CAROUSEL);

    companion object {
        fun of(id: String): ExtraShelf? = entries.firstOrNull { it.id == id }
    }
}

/** What to do to Home's shelves so they match the layout. */
internal class ShelfChanges(val sections: List<HomeSection>, val removed: Set<String>) {
    fun applyTo(current: List<HomeSection>): List<HomeSection> =
        sections.fold(current.filterNot { it.id in removed }) { acc, section -> acc.withSection(section) }
}

/**
 * Builds the shelves a layout asks for beyond the defaults: the optional shelves, and genre shelves
 * with the categories their owner picked. A shelf is only rebuilt when what it is built from
 * changes, so styling a shelf or dragging it costs no reads.
 */
internal class ExtraShelves(
    private val shelves: HomeShelfRepository,
    private val christian: ChristianShelfRepository,
    private val friends: FriendPicksRepository,
    private val context: Context
) {
    private val builtFrom = HashMap<String, ShelfConfig>()

    /** [force] rebuilds everything, as a refresh does. */
    suspend fun sync(layout: List<ShelfConfig>, current: List<HomeSection>, force: Boolean = false): ShelfChanges {
        val wanted = layout.filter { it.enabled && (it.id.startsWith(GenreShelfPrefix) || ExtraShelf.of(it.id) != null) }
        val wantedIds = wanted.mapTo(HashSet()) { it.id }
        val gone = current.map { it.id }.filter { isAddedShelf(it) && it !in wantedIds }.toSet()
        builtFrom.keys.retainAll(wantedIds)

        val built = wanted.mapNotNull { config ->
            val source = config.copy(enabled = true, style = null, count = null)
            val upToDate = !force && builtFrom[config.id] == source && current.any { it.id == config.id }
            if (upToDate) return@mapNotNull null
            builtFrom[config.id] = source
            build(config)
        }
        return ShelfChanges(built, gone)
    }

    private suspend fun build(config: ShelfConfig): HomeSection? {
        ExtraShelf.of(config.id)?.let { extra ->
            val tracks = when (extra) {
                ExtraShelf.REDISCOVER -> shelves.getForgottenFavorites(CarouselSize)
                ExtraShelf.HEAVY_ROTATION -> shelves.getTopTracks(CarouselSize)
                ExtraShelf.FRESH -> shelves.getRecentlyAdded(CarouselSize)
                ExtraShelf.LATE_NIGHT -> shelves.getLateNight(CarouselSize)
                ExtraShelf.RANDOM -> shelves.getRandom(CarouselSize)
                ExtraShelf.FRIENDS -> friends.tracks(CarouselSize)
            }
            return shelf(config.id, context.getString(extra.title), extra.style, tracks)
        }
        val tracks = genreTracks(config)
        val title = genreShelfTitle(config.categories, context.getString(R.string.shelf_christian), context.getString(R.string.shelf_genre))
        return carousel(config.id, title, tracks)
    }

    private suspend fun genreTracks(config: ShelfConfig): List<UnifiedTrack> {
        val library = shelves.getGenreTracks(config.categories.filter { it != ChristianCategory }, CarouselSize)
        val sacred = if (ChristianCategory in config.categories) {
            val languages = config.languages.mapNotNull(ChristianLanguage::fromCode).toSet()
            christian.tracks(languages, CarouselSize)
        } else {
            emptyList()
        }
        return (library + sacred).distinctBy { it.id }.shuffled().take(CarouselSize)
    }
}

/** "Rock · Jazz", or "Rock · Jazz +2" past two categories. */
internal fun genreShelfTitle(categories: List<String>, christianLabel: String, fallback: String): String {
    val names = categories.map { if (it == ChristianCategory) christianLabel else it }
    return when {
        names.isEmpty() -> fallback
        names.size <= 2 -> names.joinToString(" · ")
        else -> names.take(2).joinToString(" · ") + " +${names.size - 2}"
    }
}
