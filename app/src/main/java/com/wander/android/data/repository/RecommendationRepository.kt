package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.ShelfDao
import com.wander.android.core.database.entity.ShelfEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.RecommendedShelf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recommendation shelves Home shows above its own library-derived ones.
 *
 * These come from the backends themselves — YouTube Music's front page is YouTube Music's
 * recommender, not an approximation of it built here out of play counts. Nothing is invented: a
 * source that publishes no feed contributes nothing, so with YouTube Music signed out Home is
 * simply the Navidrome and on-device shelves, which is the behaviour the user asked for.
 *
 * Its own class rather than another method on [MusicRepository], which is already at the
 * file-size cap — the same reason [HomeShelfRepository] exists.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val popularityRepository: PopularityRepository,
    private val trackDao: TrackDao,
    private val shelfDao: ShelfDao
) {

    /**
     * The shelves to show, from cache unless it has gone stale.
     *
     * Home used to call straight through to the network on every refresh, under a timeout. Two
     * things came out of that: the feed changed shape every launch depending on how fast the
     * network answered, and a failed fetch meant no shelves at all. Now the last good feed is what
     * Home shows, and the network only ever replaces it — so the front page is stable between
     * launches and survives being offline.
     */
    suspend fun getShelves(): List<RecommendedShelf> {
        val cached = readCache()
        if (cached.isNotEmpty() && !isStale()) return cached

        val fetched = fetchAndPersist()
        // An empty fetch is a failure, not an answer: every source being offline or signed out
        // must not wipe a feed the user can still read.
        return fetched.ifEmpty { cached }
    }

    /** Forces a network refresh regardless of cache age, for an explicit pull-to-refresh. */
    suspend fun refresh(): List<RecommendedShelf> = fetchAndPersist().ifEmpty { readCache() }

    private suspend fun isStale(): Boolean {
        val last = shelfDao.lastFetchedAt() ?: return true
        return System.currentTimeMillis() - last > CACHE_TTL_MS
    }

    private suspend fun fetchAndPersist(): List<RecommendedShelf> = coroutineScope {
        val backendShelves = async {
            musicRepository.activeSources()
                .filter { it.capabilities.recommendations }
                .map { source -> async { source.getRecommendations().getOrDefault(emptyList()) } }
                .flatMap { it.await() }
        }
        // Fetched alongside the backends rather than after them: it is one request to a server the
        // app is already paired with, and making the feed wait for it would delay every other shelf
        // for the one that is optional.
        val popular = async { popularityRepository.popularTracks(CarouselTracks) }

        val shelves = buildList {
            addAll(backendShelves.await())
            // Last, so a backend's own recommender leads the feed. This shelf is the household's
            // listening, which is worth showing but is not what the user came to Home for.
            popular.await().takeIf { it.isNotEmpty() }?.let {
                add(RecommendedShelf(PopularShelfId, "Popular on Agro", it))
            }
        }

        if (shelves.isEmpty()) return@coroutineScope emptyList()

        // Cached, but never marked as library: a suggestion is something the user was shown, not
        // something they own. Room still has to know these rows exist, or liking one — or the
        // play count written when one is played — would update a row that is not there.
        val tracks = shelves.flatMap { it.tracks }.distinctBy { it.id }
        if (tracks.isNotEmpty()) {
            trackDao.upsertTracks(tracks.map { TrackEntity.fromUnifiedTrack(it, isLibrary = false) })
        }

        val now = System.currentTimeMillis()
        shelfDao.replaceAll(
            shelves.mapIndexed { index, shelf ->
                ShelfEntity(
                    id = shelf.id,
                    title = shelf.title,
                    position = index,
                    trackIds = shelf.tracks.joinToString(",") { it.id },
                    fetchedAt = now
                )
            }
        )
        shelves
    }

    /**
     * Rebuilds the shelves from the cached structure and the tracks table.
     *
     * A shelf whose tracks have all been evicted is dropped rather than shown empty, and the
     * stored order is honoured so the feed comes back the way it went in.
     */
    private suspend fun readCache(): List<RecommendedShelf> {
        val rows = shelfDao.getShelves()
        if (rows.isEmpty()) return emptyList()

        val ids = rows.flatMap { it.trackIds.split(",") }.filter { it.isNotBlank() }.distinct()
        val byId = trackDao.getTracksByIds(ids).associateBy { it.id }

        return rows.mapNotNull { row ->
            val tracks = row.trackIds.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { byId[it]?.toUnifiedTrack() }
            if (tracks.isEmpty()) null else RecommendedShelf(row.id, row.title, tracks)
        }
    }

    private companion object {
        /**
         * How long a cached feed stays good. Long enough that the front page is the same across a
         * day's launches, short enough that "fresh" still means something.
         */
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        /**
         * Stable, and not derived from the title like a backend's shelves are: it is this app that
         * names this shelf, so the cached row has to survive the day the wording changes.
         */
        const val PopularShelfId = "popular_on_agro"

        /** Matches Home's carousel size, so nothing is fetched that cannot be shown. */
        const val CarouselTracks = 20
    }
}
