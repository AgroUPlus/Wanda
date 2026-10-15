package com.wander.android.data.model

/**
 * Which ReplayGain value evens out playback.
 *
 * [AUTO] uses the album gain while an album plays straight through — so its quiet intro stays
 * quieter than its loud single, as mastered — and the track gain once tracks from different albums
 * are mixed, which is what a shuffled queue needs.
 */
enum class LoudnessMode { OFF, TRACK, ALBUM, AUTO }
