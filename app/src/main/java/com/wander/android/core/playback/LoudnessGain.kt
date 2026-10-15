package com.wander.android.core.playback

import com.wander.android.data.model.LoudnessMode
import kotlin.math.pow

/**
 * The gain chosen for one track: which value was used, and the volume factor it became. [isOff]
 * tells "levelling is switched off" apart from "levelling is on and this track has no tags".
 */
data class LoudnessDecision(val gainDb: Float?, val fromAlbum: Boolean, val factor: Float, val isOff: Boolean = false) {
    companion object {
        val UNCHANGED = LoudnessDecision(gainDb = null, fromAlbum = false, factor = 1f)
        val OFF = UNCHANGED.copy(isOff = true)
    }
}

/**
 * Turns ReplayGain values into a player volume factor.
 *
 * The factor is capped at 1: gain is applied through the player's volume rather than an audio
 * processor, because audio offload — on by default, for battery — bypasses processors entirely.
 * Volume can only attenuate, so a positive gain clamps to 0 dB: loud tracks come down, quiet ones
 * are left as they are. The same cap is why peak values need no handling: nothing is ever boosted
 * into clipping.
 *
 * Each mode falls back to the other value when its own is missing, since a file tagged with only
 * one of them is still better levelled than not at all.
 *
 * The pre-amp applies to untagged tracks too. Its point is headroom: lowering everything lets a
 * quiet track's positive gain take effect instead of clamping away. Untagged tracks are mostly loud
 * modern masters, and leaving them at full volume would make them jump out by exactly that much.
 */
internal object LoudnessGain {

    fun decide(info: ReplayGainInfo, mode: LoudnessMode, preampDb: Int, inAlbumRun: Boolean): LoudnessDecision {
        if (mode == LoudnessMode.OFF) return LoudnessDecision.OFF
        val preferAlbum = mode == LoudnessMode.ALBUM || (mode == LoudnessMode.AUTO && inAlbumRun)
        val primary = if (preferAlbum) info.albumGainDb else info.trackGainDb
        val fallback = if (preferAlbum) info.trackGainDb else info.albumGainDb
        val gainDb = primary ?: fallback
        return LoudnessDecision(
            gainDb = gainDb,
            fromAlbum = gainDb != null && (if (primary != null) preferAlbum else !preferAlbum),
            factor = dbToFactor((gainDb ?: 0f) + preampDb)
        )
    }

    /** Decibels to an amplitude ratio: 20·log10, inverted. */
    private fun dbToFactor(db: Float): Float = AMPLITUDE_BASE.pow(db / AMPLITUDE_DB_PER_DECADE).coerceIn(0f, 1f)

    private const val AMPLITUDE_BASE = 10f
    private const val AMPLITUDE_DB_PER_DECADE = 20f
}
