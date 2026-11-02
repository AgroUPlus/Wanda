package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Builds a station around one seed track.
 *
 * Candidates come only from signals that mean "sounds like this", never from a text match on the
 * seed's name or the last search:
 * - each backend's own radio endpoint for the seed's rendition there (Navidrome `getSimilarSongs2`,
 *   YouTube Music's watch-playlist radio, Deezer's track radio);
 * - the indexed tracks whose stored audio embedding is nearest the seed's ([SonicNeighbours]);
 * - the user's own collection sharing the seed's primary artist or genre — what keeps a station
 *   going when a backend answers with nothing, as Navidrome does without a metadata agent.
 *
 * The three are taken in turn rather than one after another, so a backend that answers with forty
 * tracks cannot crowd the library's closest matches out of a twenty-track batch.
 *
 * [RadioPool] then drops the seed, its renditions and other uploads of the same song, and the
 * acoustic vectors order what is left.
 */
internal class SmartRadioGenerator(
    private val trackDao: TrackDao,
    private val activeSources: () -> List<IMusicSource>,
    private val recordingRules: RecordingRulesRepository,
    private val acousticFeatures: AcousticFeatureRepository,
    private val sonicNeighbours: SonicNeighbours,
    private val renditionsOf: suspend (UnifiedTrack) -> List<UnifiedTrack>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit
) {
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val fromSource = radioAcrossSources(seed, count * 2)
            if (fromSource.isNotEmpty()) persist(fromSource, false)

            val fromLibrary = libraryNeighbours(seed, count * 2)
            val soundsLike = soundsLike(seed, count)

            val pool = RadioPool.of(
                seed = seed,
                candidates = RadioPool.roundRobin(interleaveBySource(fromSource), soundsLike, fromLibrary),
                rules = recordingRules.current()
            )
            if (pool.isEmpty()) return@withContext emptyList()

            val vectors = acousticFeatures.allFeatures()
            SmartRadioBuilder.build(
                seed = vectors[seed.id] ?: acousticFeatures.featuresFor(seed.id),
                candidates = pool.map { SmartRadioBuilder.Candidate(it, vectors[it.id]) },
                count = count
            )
        }

    /** The seed's artist and genre in the user's own collection. */
    private suspend fun libraryNeighbours(seed: UnifiedTrack, limit: Int): List<UnifiedTrack> =
        trackDao.getSeedNeighbours(
            seedId = seed.id,
            artist = ArtistCredits.leadName(seed.artist),
            genre = seed.genre?.trim()?.takeIf { it.isNotEmpty() },
            limit = limit
        ).map(TrackEntity::toUnifiedTrack)

    /** [SonicNeighbours] for [seed], as tracks, in their nearest-first order. */
    private suspend fun soundsLike(seed: UnifiedTrack, limit: Int): List<UnifiedTrack> {
        val ids = sonicNeighbours.nearest(seed.id, limit)
        if (ids.isEmpty()) return emptyList()
        val byId = trackDao.getTracksByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toUnifiedTrack() }.filterNot { it.isEpisode || it.isLive }
    }

    private suspend fun radioAcrossSources(seed: UnifiedTrack, count: Int): List<UnifiedTrack> =
        coroutineScope {
            val seedPerSource = (renditionsOf(seed) + seed).associateBy { it.source }
            activeSources()
                .filter { it.capabilities.radio }
                .mapNotNull { source ->
                    val id = seedPerSource[source.sourceType]?.id ?: return@mapNotNull null
                    async { source.getRadio(id, count).getOrNull().orEmpty() }
                }
                .flatMap { it.await() }
        }
}
