package com.wander.android.data.repository

import com.wander.android.core.playback.ActualAudioFormat
import com.wander.android.data.model.UnifiedTrack
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A lossless (or higher-bitrate) copy of what's currently playing, found in another source.
 *
 * Carries data rather than a pre-built sentence: this repository has no `Context` and cannot
 * localize text, so it hands the UI what it found — [format], [is24Bit], [targetTrack.source] —
 * and the string with the placeholders for those lives in `strings.xml` for whatever composable
 * ends up showing this offer to format.
 */
data class QualityUpgradeOffer(
    val targetTrack: UnifiedTrack,
    val format: String,
    val is24Bit: Boolean
)

/**
 * Offers to switch to a better copy of the track that's playing right now.
 *
 * Only fires off the *actual* decoded format — [ActualAudioFormat], captured from ExoPlayer —
 * never the static container tags a backend reports, because those can be wrong: a Navidrome
 * track tagged FLAC still gets transcoded if the server's rules say so, and offering an "upgrade"
 * to the very file already playing would be a lie the badge told.
 */
@Singleton
class QualityUpgradeAdvisor @Inject constructor(
    private val musicRepository: MusicRepository
) {

    private val losslessFormats = setOf("FLAC", "ALAC", "WAV", "AIFF")

    /** Null when nothing is playing, the current stream is already lossless, or no match exists. */
    suspend fun findUpgrade(current: UnifiedTrack, actual: ActualAudioFormat?): QualityUpgradeOffer? {
        if (actual == null || actual.isLossless) return null

        val candidates = musicRepository.searchAllSources("${current.artist} ${current.title}")
            .filter { it.id != current.id && isLossless(it) }
        if (candidates.isEmpty()) return null

        val link = UniversalTrackLink(
            title = current.title,
            artist = current.artist,
            album = current.album,
            durationMs = current.durationMs.takeIf { it > 0 }
        )
        val best = TrackResolution.bestMatch(link, candidates) ?: return null

        return QualityUpgradeOffer(
            targetTrack = best,
            format = best.format?.uppercase(Locale.US) ?: "FLAC",
            is24Bit = (actual.bitDepth ?: 0) >= 24
        )
    }

    private fun isLossless(track: UnifiedTrack): Boolean =
        track.format?.uppercase(Locale.US)?.trim() in losslessFormats
}
