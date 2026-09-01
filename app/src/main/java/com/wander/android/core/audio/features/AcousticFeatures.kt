package com.wander.android.core.audio.features

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What one recording sounds like, in six numbers.
 *
 * Every field is normalised to `0..1` (the key pair to `-1..1`) so that distance between two of
 * these means something without a scaling step: a vector whose components ran in raw units would
 * let tempo, measured in the hundreds, drown out everything else.
 *
 * These describe the *performance*, not the file. Two transfers of one recording produce nearly
 * identical vectors, which is what makes them usable for "more like this"; a live take of the same
 * song produces a different one, which is the whole point.
 */
data class AcousticFeatures(
    /** Beats per minute, mapped over [MIN_BPM]..[MAX_BPM]. */
    val tempo: Float,
    /** Loudness on a log scale. Quiet folk sits low, a compressed mix sits high. */
    val energy: Float,
    /** Spectral centroid: where the weight of the sound sits. Dull and dark, or bright and hissy. */
    val brightness: Float,
    /** How strongly and regularly the track pulses. A drum machine beats a rubato piano. */
    val danceability: Float,
    /**
     * Key, as a point on the circle of fifths rather than a pitch-class number.
     *
     * A number would make C (0) and B (11) the furthest apart things in the vector when they are a
     * semitone away, and would make the fifth — the one relationship that actually governs whether
     * two songs sit well together — invisible. Projected onto a circle, neighbouring keys are
     * neighbouring points and the distance is the musical one.
     */
    val keyX: Float,
    val keyY: Float
) {

    /**
     * Euclidean distance, with the axes weighted by how much each one governs whether two tracks
     * belong in the same queue.
     *
     * Not cosine: these are absolute positions, not directions. Two tracks at 60 and 180 BPM point
     * the same way from the origin and are not remotely alike.
     */
    fun distanceTo(other: AcousticFeatures): Float {
        var sum = 0f
        sum += TEMPO_WEIGHT * square(tempo - other.tempo)
        sum += ENERGY_WEIGHT * square(energy - other.energy)
        sum += BRIGHTNESS_WEIGHT * square(brightness - other.brightness)
        sum += DANCE_WEIGHT * square(danceability - other.danceability)
        // The key pair is one axis, not two: it is a single position on a circle and its two
        // coordinates are not independent.
        sum += KEY_WEIGHT * (square(keyX - other.keyX) + square(keyY - other.keyY))
        return sqrt(sum)
    }

    /** Whether anything was actually measured. A silent or undecodable track lands here. */
    val isUsable: Boolean
        get() = energy > 0f && !tempo.isNaN() && !brightness.isNaN()

    private fun square(value: Float) = value * value

    companion object {
        /**
         * The tempo range worth telling apart. Below 60 BPM a listener hears the half-time pulse
         * anyway, and above 180 the autocorrelation is measuring the double-time one.
         */
        const val MIN_BPM = 60f
        const val MAX_BPM = 180f

        /**
         * Tempo and energy carry the most: a queue that changes speed or volume abruptly is the
         * one a listener notices. Key matters, but a good segue across keys is ordinary and a
         * wrong-tempo segue never is.
         */
        const val TEMPO_WEIGHT = 1.6f
        const val ENERGY_WEIGHT = 1.3f
        const val BRIGHTNESS_WEIGHT = 0.8f
        const val DANCE_WEIGHT = 1.0f
        const val KEY_WEIGHT = 0.6f

        /** Maps a measured BPM onto the stored `0..1` axis, clamped to the range above. */
        fun normaliseTempo(bpm: Float): Float =
            ((bpm - MIN_BPM) / (MAX_BPM - MIN_BPM)).coerceIn(0f, 1f)

        /** The BPM back out of the stored value, for anything that wants to show it. */
        fun bpmOf(tempo: Float): Float = MIN_BPM + tempo * (MAX_BPM - MIN_BPM)

        /**
         * A pitch class (0 = C … 11 = B) as a point on the circle of fifths.
         *
         * [strength] scales it toward the origin, so a track with no clear tonal centre — noise, a
         * drum solo — sits in the middle and is not asserted to be in C.
         */
        fun keyPoint(pitchClass: Int, strength: Float): Pair<Float, Float> {
            val position = (pitchClass * FIFTHS_STEP) % 12
            val angle = 2.0 * Math.PI * position / 12.0
            val scale = strength.coerceIn(0f, 1f)
            return (Math.cos(angle).toFloat() * scale) to (Math.sin(angle).toFloat() * scale)
        }

        /** Seven semitones is a fifth; stepping by it walks the circle. */
        private const val FIFTHS_STEP = 7

        /** How far apart two vectors may sit and still be called neighbours. */
        fun isNear(distance: Float, limit: Float) = abs(distance) <= limit
    }
}
