package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds a queue from a point on the Mood & Energy matrix, using the vectors
 * [AcousticFeatureRepository] already measured for every decoded track.
 *
 * Ranks on tempo and energy alone, not the full [com.wander.android.core.audio.features.AcousticFeatures]
 * distance — the matrix is a 2D control and using the other three axes (brightness, danceability,
 * key) would rank candidates on dimensions the person never touched.
 */
@Singleton
class MoodRadioRepository @Inject constructor(
    private val featureRepository: AcousticFeatureRepository,
    private val trackDao: TrackDao
) {

    /**
     * The [limit] closest tracks to ([tempo], [energy]) — both normalised `0..1`, the same axis
     * [com.wander.android.core.audio.features.AcousticFeatures.tempo] and `.energy` are stored on
     * — ordered nearest first.
     */
    suspend fun radioFor(tempo: Float, energy: Float, limit: Int = 30): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val nearestIds = MoodRadioRanking.nearest(featureRepository.allFeatures(), tempo, energy, limit)
            if (nearestIds.isEmpty()) return@withContext emptyList()

            // Bulk lookup, not one query per candidate — same reasoning as `getTracksByIds`'s own
            // doc comment: rebuilding a shelf from N ids is one round trip, not N of them.
            val byId = trackDao.getTracksByIds(nearestIds).associateBy { it.id }
            nearestIds.mapNotNull { id -> byId[id]?.toUnifiedTrack() }
        }
}

/**
 * A named point on the matrix, for the quick-pick pills.
 *
 * [key] rather than a display label: this is a plain repository with no `Context`, and the label
 * shown for each key lives in `strings.xml` — see `MoodMatrixCard`'s `MoodPreset.label()`, which
 * is the only place these keys are turned into text.
 */
data class MoodPreset(
    val key: String,
    val tempo: Float,
    val energy: Float
)

val MoodPresets = listOf(
    MoodPreset("late_night_chill", tempo = 0.2f, energy = 0.15f),
    MoodPreset("focus_flow", tempo = 0.4f, energy = 0.35f),
    MoodPreset("morning_energizer", tempo = 0.65f, energy = 0.6f),
    MoodPreset("high_voltage", tempo = 0.9f, energy = 0.95f)
)
