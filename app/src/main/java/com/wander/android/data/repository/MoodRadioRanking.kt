package com.wander.android.data.repository

import com.wander.android.core.audio.features.AcousticFeatures
import kotlin.math.sqrt

/**
 * The pure half of [MoodRadioRepository]: which track ids are closest to a point on the Tempo ×
 * Energy matrix. Kept separate from the DB reads for the same reason [TrackResolution] is kept
 * separate from [LinkRepository] — this is the part with a decision in it, and it is testable on
 * the JVM without a database.
 */
internal object MoodRadioRanking {

    /**
     * The [limit] closest ids to ([tempo], [energy]), nearest first. Ranks on tempo and energy
     * alone — the matrix is a 2D control, and using [AcousticFeatures]'s other axes (brightness,
     * danceability, key) would rank on dimensions nobody touched. Tracks with no usable
     * measurement ([AcousticFeatures.isUsable] false) are excluded rather than treated as a match.
     */
    fun nearest(features: Map<String, AcousticFeatures>, tempo: Float, energy: Float, limit: Int): List<String> {
        val t = tempo.coerceIn(0f, 1f)
        val e = energy.coerceIn(0f, 1f)
        return features.entries
            .asSequence()
            .filter { it.value.isUsable }
            .sortedBy { distance(it.value.tempo, it.value.energy, t, e) }
            .take(limit)
            .map { it.key }
            .toList()
    }

    private fun distance(tempoA: Float, energyA: Float, tempoB: Float, energyB: Float): Float {
        val dt = tempoA - tempoB
        val de = energyA - energyB
        return sqrt(dt * dt + de * de)
    }
}
