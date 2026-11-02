package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import com.wander.android.data.model.EXTRA_REPLAYGAIN_ALBUM_DB
import com.wander.android.data.model.EXTRA_REPLAYGAIN_TRACK_DB
import com.wander.android.data.model.UnifiedTrack

/** A file's ReplayGain values in dB, relative to ReplayGain 2.0's −18 LUFS. Null when untagged. */
data class ReplayGainInfo(val trackGainDb: Float?, val albumGainDb: Float?) {
    /** This info, with each value it lacks taken from [other]. */
    fun orElse(other: ReplayGainInfo) =
        ReplayGainInfo(trackGainDb ?: other.trackGainDb, albumGainDb ?: other.albumGainDb)

    companion object {
        val NONE = ReplayGainInfo(null, null)
    }
}

/**
 * Reads ReplayGain out of the tags Media3 already parsed while extracting the stream, so nothing
 * opens the file a second time.
 *
 * The same two values are spelled differently per container:
 * - FLAC, Ogg and Opus carry `REPLAYGAIN_*` Vorbis comments, or for Opus `R128_*` ones;
 * - MP3 carries ID3 `TXXX` frames, and LAME writes a gain into its own Info header;
 * - MP4 carries iTunes freeform atoms, which Media3 surfaces as [InternalFrame]s.
 *
 * The explicit `REPLAYGAIN_*` tag wins over the others for each value: it is the one a tagger
 * wrote on purpose. A value that does not parse is skipped rather than guessed at.
 */
@OptIn(UnstableApi::class)
internal object ReplayGainTags {

    fun read(metadata: Metadata?): ReplayGainInfo {
        if (metadata == null) return ReplayGainInfo.NONE
        val found = Found()
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is VorbisComment -> found.vorbis(entry.key, entry.value)
                is TextInformationFrame -> if (entry.id in TXXX_IDS) {
                    entry.values.firstOrNull()?.let { found.explicit(entry.description, it) }
                }
                is InternalFrame -> found.explicit(entry.description, entry.text)
                is Mp3InfoReplayGain -> listOfNotNull(entry.field1, entry.field2).forEach(found::lame)
            }
        }
        return found.info()
    }

    /** What each tag family said, kept apart so the explicit tag can win whatever order they came in. */
    private class Found {
        var track: Float? = null
        var album: Float? = null
        var r128Track: Float? = null
        var r128Album: Float? = null
        var lameTrack: Float? = null
        var lameAlbum: Float? = null

        fun explicit(name: String?, value: String) {
            when (name?.uppercase()) {
                TRACK_GAIN -> track = track ?: parseDb(value)
                ALBUM_GAIN -> album = album ?: parseDb(value)
            }
        }

        fun vorbis(key: String, value: String) {
            when (key.uppercase()) {
                R128_TRACK_GAIN -> r128Track = r128Track ?: parseR128(value)
                R128_ALBUM_GAIN -> r128Album = r128Album ?: parseR128(value)
                else -> explicit(key, value)
            }
        }

        fun lame(field: Mp3InfoReplayGain.GainField) {
            if (field.originator == Mp3InfoReplayGain.GainField.ORIGINATOR_UNSET) return
            when (field.name) {
                Mp3InfoReplayGain.GainField.NAME_RADIO -> lameTrack = lameTrack ?: plausible(field.gain)
                Mp3InfoReplayGain.GainField.NAME_AUDIOPHILE -> lameAlbum = lameAlbum ?: plausible(field.gain)
            }
        }

        fun info() = ReplayGainInfo(
            trackGainDb = track ?: r128Track ?: lameTrack,
            albumGainDb = album ?: r128Album ?: lameAlbum
        )
    }

    /**
     * What the track's source reported, from [UnifiedTrack.extraData] — the fallback for a stream
     * whose tags say nothing, such as one the server transcoded. Held to the same bounds as a tag.
     */
    fun fromSource(extras: Map<String, String>): ReplayGainInfo = ReplayGainInfo(
        trackGainDb = extras[EXTRA_REPLAYGAIN_TRACK_DB]?.toFloatOrNull()?.let { plausible(it) },
        albumGainDb = extras[EXTRA_REPLAYGAIN_ALBUM_DB]?.toFloatOrNull()?.let { plausible(it) }
    )

    /**
     * [db], or null when no real track could carry it. Every family goes through this — the LAME
     * header's gain used to be taken as read, and that header is written (and mis-written) by
     * encoders other than LAME: a stray −40 dB in it turned a track down to 1% and it played as
     * silence, with nothing on screen to say why.
     */
    internal fun plausible(db: Float): Float? = db.takeIf { it.isFinite() && it in PLAUSIBLE_DB }

    /** `"-6.50 dB"`, `"+3.2 dB"`, `"-6,5"`: the leading number, as dB. */
    internal fun parseDb(raw: String): Float? {
        val number = DB_PATTERN.find(raw.trim())?.groupValues?.get(1)?.replace(',', '.') ?: return null
        return number.toFloatOrNull()?.let { plausible(it) }
    }

    /**
     * RFC 7845 §5.2.1: a Q7.8 fixed-point integer relative to −23 LUFS (EBU R128). Adding 5 dB
     * moves it onto ReplayGain 2.0's −18 LUFS reference, so both tag families mean the same thing.
     */
    internal fun parseR128(raw: String): Float? {
        val q78 = raw.trim().toIntOrNull() ?: return null
        return plausible(q78 / Q78_ONE + R128_TO_REPLAYGAIN_DB)
    }

    private const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    private const val ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    private const val R128_TRACK_GAIN = "R128_TRACK_GAIN"
    private const val R128_ALBUM_GAIN = "R128_ALBUM_GAIN"
    private const val R128_TO_REPLAYGAIN_DB = 5f
    private const val Q78_ONE = 256f

    /** `TXXX` in ID3v2.3/2.4, `TXX` in ID3v2.2. */
    private val TXXX_IDS = setOf("TXXX", "TXX")
    private val DB_PATTERN = Regex("""^([+-]?\d+(?:[.,]\d+)?)""")

    /**
     * A value outside this is a broken tag, not a loud or quiet track. The floor is physical: the
     * loudest thing a digital file can hold, a full-scale square wave, measures about +3 LUFS, which
     * is −21 dB against the −18 LUFS reference — so no honest gain is below −24 dB. The ceiling only
     * mirrors it; a positive gain is capped to 0 dB by [LoudnessGain] anyway.
     */
    private val PLAUSIBLE_DB = -24f..24f
}
