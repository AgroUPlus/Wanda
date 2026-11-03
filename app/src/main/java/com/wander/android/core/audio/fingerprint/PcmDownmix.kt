package com.wander.android.core.audio.fingerprint

import android.media.AudioFormat as PlatformAudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns one `MediaCodec` output buffer into mono samples, straight into a [StreamingResampler].
 *
 * The decoder used to assume 16-bit PCM unconditionally. That is the default for the platform's
 * compressed-audio decoders, but not a promise: the raw decoder hands WAV through in its own
 * encoding, and a decoder may emit float. Read as shorts, a float buffer is noise at twice the
 * frame count — a fingerprint of nothing, stored as if it were the track. The encoding now comes
 * from the codec's output format and anything not handled here is refused, not guessed at.
 */
internal object PcmDownmix {

    /** Whether [encoding] (an `AudioFormat.ENCODING_PCM_*` value) can be read. */
    fun supports(encoding: Int): Boolean = bytesPerSample(encoding) > 0

    /**
     * Averages the [channels] interleaved channels of [bytes] (position to limit) down to one
     * and pushes each frame into [into]. Returns the frames read.
     */
    fun mix(bytes: ByteBuffer, channels: Int, encoding: Int, into: StreamingResampler): Int {
        val pcm = bytes.slice().order(ByteOrder.nativeOrder())
        val width = bytesPerSample(encoding)
        require(width > 0 && channels > 0) { "unsupported PCM shape: $encoding × $channels" }
        val frames = pcm.remaining() / (width * channels)
        when (encoding) {
            // Same arithmetic, in the same order, as before the other encodings existed — the
            // 16-bit path is what every stored fingerprint was made with.
            PlatformAudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = pcm.asShortBuffer()
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        sum += shorts[frame * channels + channel] / Short.MAX_VALUE.toFloat()
                    }
                    into.push(sum / channels)
                }
            }
            PlatformAudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = pcm.asFloatBuffer()
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) sum += floats[frame * channels + channel]
                    into.push(sum / channels)
                }
            }
            else -> for (frame in 0 until frames) {
                var sum = 0f
                for (channel in 0 until channels) {
                    sum += sampleAt(pcm, (frame * channels + channel) * width, encoding)
                }
                into.push(sum / channels)
            }
        }
        return frames
    }

    private fun sampleAt(pcm: ByteBuffer, offset: Int, encoding: Int): Float = when (encoding) {
        PlatformAudioFormat.ENCODING_PCM_8BIT ->
            ((pcm.get(offset).toInt() and 0xFF) - 128) / 128f
        // Native order, which is little-endian on every ABI Android ships; the top byte keeps its
        // sign so the shift sign-extends.
        ENCODING_PCM_24BIT_PACKED -> {
            val b0 = pcm.get(offset).toInt() and 0xFF
            val b1 = pcm.get(offset + 1).toInt() and 0xFF
            val b2 = pcm.get(offset + 2).toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608f
        }
        ENCODING_PCM_32BIT -> pcm.getInt(offset) / 2_147_483_648f
        else -> error("unsupported PCM encoding $encoding")
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        PlatformAudioFormat.ENCODING_PCM_8BIT -> 1
        PlatformAudioFormat.ENCODING_PCM_16BIT -> 2
        ENCODING_PCM_24BIT_PACKED -> 3
        PlatformAudioFormat.ENCODING_PCM_FLOAT, ENCODING_PCM_32BIT -> 4
        else -> 0
    }

    // `AudioFormat.ENCODING_PCM_24BIT_PACKED` and `ENCODING_PCM_32BIT` are API 31 names for
    // values a decoder on an older release can still report; spelled out so minSdk 26 reads them.
    private const val ENCODING_PCM_24BIT_PACKED = 21
    private const val ENCODING_PCM_32BIT = 22
}
