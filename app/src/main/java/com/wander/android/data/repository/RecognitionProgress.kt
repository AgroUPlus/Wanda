package com.wander.android.data.repository

/**
 * One track the clip currently looks like, as a screen needs to draw it.
 *
 * [lead] rather than [votes] is what a confidence bar should be drawn from: votes carry a floor of
 * coincidences that grows with the size of the index and the length of the clip, and a bar drawn
 * from them would sit two-thirds full for every candidate including the wrong ones. The lead is
 * the quantity the decision is actually taken on.
 */
data class RecognitionCandidate(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val votes: Int,
    val lead: Int
)

/**
 * What the matcher believes so far.
 *
 * Emitted about once a second while the microphone is open, and once more when it closes. The
 * ranking really does change between emissions — a leader can be overtaken as more of a chorus
 * arrives — and that is the point: this is the engine's state, not an animation of one.
 */
data class RecognitionProgress(
    val candidates: List<RecognitionCandidate>,
    /** The typical candidate's score, which is what the leader has to stand above. */
    val noiseFloor: Int,
    /** True on the last emission only. */
    val settled: Boolean,
    /** Set only when settled, and null there when nothing was confident enough to name. */
    val recognition: Recognition?
)
