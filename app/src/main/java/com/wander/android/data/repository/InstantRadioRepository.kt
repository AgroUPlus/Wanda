package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A station built from nothing — no seed track, no chosen playlist, no decisions.
 *
 * The existing radio ([MusicRepository.generateRadio]) extends whatever is already playing. This
 * answers the other question: "just play me something", when the queue is empty and picking a
 * starting point is itself the friction.
 *
 * Two halves, because either alone is wrong. Taste comes from Room — the tracks actually played,
 * loved, and played *recently*, which is the only honest record of what someone likes. Discovery
 * comes from asking each backend for radio around those picks, so the station reaches past the
 * library instead of reshuffling it.
 *
 * Sources are then interleaved round-robin rather than concatenated. Ranking the pool by any
 * single score hands the whole station to whichever backend happens to answer with the most
 * tracks, and a station that is 40 YouTube Music tracks is not a station across every source.
 */
@Singleton
class InstantRadioRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val musicRepository: MusicRepository
) {

    /**
     * A ready-to-play station, or empty when there is nothing to build one from.
     *
     * Empty is a real answer: a library with no plays and no likes has said nothing about what
     * its owner wants to hear, and inventing a station out of an arbitrary slice of it would be
     * worse than saying so.
     */
    suspend fun buildStation(size: Int = STATION_SIZE): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val seeds = pickSeeds()
            if (seeds.isEmpty()) return@withContext emptyList()

            val discovered = coroutineScope {
                seeds.take(SEED_COUNT)
                    .map { seed -> async { musicRepository.generateRadio(seed, PER_SEED) } }
                    .flatMap { it.await() }
            }

            val pool = TrackDeduplicator.deduplicate(seeds + discovered)
            interleaveBySource(pool).take(size)
        }

    /**
     * The taste sample: what is played most, what is loved, what is on rotation right now.
     *
     * Weighted by how many of each is taken rather than by a score. Top-played says what someone
     * returns to, liked says what they chose deliberately, and recently-played says what they are
     * into *this month* — a station made only of all-time favourites is a station that never
     * changes.
     */
    private suspend fun pickSeeds(): List<UnifiedTrack> {
        val top = trackDao.getTopPlayedTracks(TOP_SAMPLE).map(TrackEntity::toUnifiedTrack)
        val liked = trackDao.getLikedTracksList(LIKED_SAMPLE).map(TrackEntity::toUnifiedTrack)
        val recent = trackDao.getRecentlyPlayedTracks(RECENT_SAMPLE).map(TrackEntity::toUnifiedTrack)

        return TrackDeduplicator.deduplicate(
            top.shuffled().take(TOP_TAKE) +
                liked.shuffled().take(LIKED_TAKE) +
                recent.shuffled().take(RECENT_TAKE)
        ).shuffled()
    }

    /** One track from each source in turn, until every source has run out. */
    private fun interleaveBySource(tracks: List<UnifiedTrack>): List<UnifiedTrack> {
        val bySource: Map<SourceType, List<UnifiedTrack>> = tracks.groupBy { it.source }
        val queues = bySource.values.map { it.shuffled().toMutableList() }
        return buildList {
            while (queues.any { it.isNotEmpty() }) {
                queues.forEach { queue -> if (queue.isNotEmpty()) add(queue.removeAt(0)) }
            }
        }
    }

    private companion object {
        const val STATION_SIZE = 40

        /** Enough seeds for variety, few enough that the station starts without a visible wait. */
        const val SEED_COUNT = 5
        const val PER_SEED = 12

        const val TOP_SAMPLE = 60
        const val LIKED_SAMPLE = 60
        const val RECENT_SAMPLE = 40

        const val TOP_TAKE = 4
        const val LIKED_TAKE = 3
        const val RECENT_TAKE = 3
    }
}
