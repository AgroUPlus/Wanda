package com.wander.android.core.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTagsTest {

    @Test
    fun readsVorbisCommentsWhateverTheirCase() {
        val info = ReplayGainTags.read(
            Metadata(
                VorbisComment("replaygain_track_gain", "-6.50 dB"),
                VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-7.25 dB")
            )
        )

        assertEquals(ReplayGainInfo(-6.5f, -7.25f), info)
    }

    @Test
    fun readsId3TxxxFrames() {
        val info = ReplayGainTags.read(
            Metadata(
                TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("+2.10 dB")),
                TextInformationFrame("TXXX", "replaygain_album_gain", listOf("-1.00 dB"))
            )
        )

        assertEquals(ReplayGainInfo(2.1f, -1f), info)
    }

    @Test
    fun ignoresTextFramesThatAreNotTxxx() {
        val info = ReplayGainTags.read(Metadata(TextInformationFrame("TIT2", "REPLAYGAIN_TRACK_GAIN", listOf("-3 dB"))))

        assertEquals(ReplayGainInfo.NONE, info)
    }

    @Test
    fun readsMp4FreeformAtoms() {
        val info = ReplayGainTags.read(Metadata(InternalFrame("com.apple.iTunes", "replaygain_track_gain", "-4.2 dB")))

        assertEquals(-4.2f, info.trackGainDb!!, 0.001f)
    }

    @Test
    fun convertsOpusR128GainsOntoTheReplayGainReference() {
        // -2560 / 256 = -10 dB relative to -23 LUFS, which is -5 dB relative to -18 LUFS.
        val info = ReplayGainTags.read(Metadata(VorbisComment("R128_TRACK_GAIN", "-2560")))

        assertEquals(-5f, info.trackGainDb!!, 0.001f)
    }

    @Test
    fun anExplicitReplayGainTagWinsOverR128() {
        val info = ReplayGainTags.read(
            Metadata(VorbisComment("R128_TRACK_GAIN", "-2560"), VorbisComment("REPLAYGAIN_TRACK_GAIN", "-3 dB"))
        )

        assertEquals(-3f, info.trackGainDb!!, 0.001f)
    }

    @Test
    fun skipsMalformedValuesInsteadOfGuessing() {
        val info = ReplayGainTags.read(
            Metadata(VorbisComment("REPLAYGAIN_TRACK_GAIN", "loud"), VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-900 dB"))
        )

        assertEquals(ReplayGainInfo.NONE, info)
    }

    @Test
    fun parsesCommaDecimalsAndBareNumbers() {
        assertEquals(-6.5f, ReplayGainTags.parseDb("-6,5 dB")!!, 0.001f)
        assertEquals(1f, ReplayGainTags.parseDb(" 1 ")!!, 0.001f)
        assertNull(ReplayGainTags.parseDb("dB -6"))
    }

    @Test
    fun noMetadataMeansNoGain() {
        assertEquals(ReplayGainInfo.NONE, ReplayGainTags.read(null))
        assertEquals(ReplayGainInfo.NONE, ReplayGainTags.read(Metadata()))
    }
}
