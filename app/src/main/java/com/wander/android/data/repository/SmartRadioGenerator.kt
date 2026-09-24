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
 * Generates continuous smart radio queues using acoustic vector embeddings and multi-backend stations.
 */
internal class SmartRadioGenerator(
    private val trackDao: TrackDao,
    private val activeSources: () -> List<IMusicSource>,
    private val recordingRules: RecordingRulesRepository,
    private val acousticFeatures: AcousticFeatureRepository,
    private val renditionsOf: suspend (UnifiedTrack) -> List<UnifiedTrack>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit
) {
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val fromSource = radioAcrossSources(seed, count * 2)
            if (fromSource.isNotEmpty()) persist(fromSource, false)

            val fromLibrary = trackDao.getTopPlayedTracks(count * 2)
                .map(TrackEntity::toUnifiedTrack)

            val pool = recordingRules.current()
                .distinct(interleaveBySource(fromSource) + fromLibrary)
                .filter { it.id != seed.id }
            if (pool.isEmpty()) return@withContext emptyList()

            val vectors = acousticFeatures.allFeatures()
            SmartRadioBuilder.build(
                seed = vectors[seed.id] ?: acousticFeatures.featuresFor(seed.id),
                candidates = pool.map { SmartRadioBuilder.Candidate(it, vectors[it.id]) },
                count = count
            )
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
