package com.wander.android.data.repository

import com.wander.android.core.audio.features.AcousticFeatures
import com.wander.android.core.audio.features.FeatureExtractor
import com.wander.android.core.database.dao.TrackFeatureDao
import com.wander.android.core.database.entity.TrackFeatureEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the measured vector for every track this device has decoded.
 *
 * The measurement itself is [FeatureExtractor]'s; this owns when it is taken, where it is kept and
 * when it stops being true. Nothing here decodes — it is handed samples by the indexer that had
 * already paid for them.
 */
@Singleton
class AcousticFeatureRepository @Inject constructor(
    private val featureDao: TrackFeatureDao,
    private val extractor: FeatureExtractor
) {

    /** Measures [samples] and stores the result. A track that measures to nothing is skipped. */
    suspend fun measure(trackId: String, samples: FloatArray) = withContext(Dispatchers.IO) {
        val features = extractor.extract(samples) ?: return@withContext
        featureDao.upsert(
            TrackFeatureEntity(
                trackId = trackId,
                tempo = features.tempo,
                energy = features.energy,
                brightness = features.brightness,
                danceability = features.danceability,
                keyX = features.keyX,
                keyY = features.keyY,
                version = EXTRACTOR_VERSION,
                measuredAt = System.currentTimeMillis()
            )
        )
    }

    /** Which of the tracks on this device still need measuring. */
    suspend fun needingMeasurement(limit: Int): Set<String> = withContext(Dispatchers.IO) {
        featureDao.needingMeasurement(EXTRACTOR_VERSION, limit).toSet()
    }

    suspend fun featuresFor(trackId: String): AcousticFeatures? = withContext(Dispatchers.IO) {
        featureDao.get(trackId)?.toFeatures()
    }

    /**
     * Every vector, by track id.
     *
     * One read for a whole radio build rather than a lookup per candidate — see the note on
     * [TrackFeatureDao.getAll].
     */
    suspend fun allFeatures(): Map<String, AcousticFeatures> = withContext(Dispatchers.IO) {
        featureDao.getAll(EXTRACTOR_VERSION).associate { it.trackId to it.toFeatures() }
    }

    /** Forgets rows for departed tracks and for older versions of the extractor. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        featureDao.prune(EXTRACTOR_VERSION)
    }

    private fun TrackFeatureEntity.toFeatures() = AcousticFeatures(
        tempo = tempo,
        energy = energy,
        brightness = brightness,
        danceability = danceability,
        keyX = keyX,
        keyY = keyY
    )

    companion object {
        /**
         * Bumped whenever [FeatureExtractor] changes what it measures.
         *
         * Stored vectors from an older version are not comparable with new ones — a distance
         * between two different definitions of brightness is a number with no meaning — so they
         * are treated as absent and remeasured rather than mixed.
         */
        const val EXTRACTOR_VERSION = 1
    }
}
