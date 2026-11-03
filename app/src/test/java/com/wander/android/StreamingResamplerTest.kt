package com.wander.android

import com.wander.android.core.audio.fingerprint.FloatBuffer
import com.wander.android.core.audio.fingerprint.PcmDownmix
import com.wander.android.core.audio.fingerprint.Resampler
import com.wander.android.core.audio.fingerprint.StreamingResampler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decoder resamples as it goes and the microphone resamples a finished clip. Both sides of a
 * match must produce the same samples, so the streaming version is held to exact equality.
 */
class StreamingResamplerTest {

    private fun stream(input: FloatArray, rate: Int): FloatArray {
        val out = FloatBuffer(16)
        val resampler = StreamingResampler(rate, out)
        input.forEach(resampler::push)
        resampler.finish()
        return out.toFloatArray()
    }

    @Test
    fun `streaming output equals the batch resampler at common source rates`() {
        val random = Random(42)
        for (rate in listOf(44_100, 48_000, 22_050, 16_000, 11_025, 96_000, 6_000)) {
            for (length in listOf(0, 1, 2, 7, 101, 4_410, 44_101)) {
                val input = FloatArray(length) { random.nextFloat() * 2f - 1f }
                assertArrayEquals(
                    "rate $rate, length $length",
                    Resampler.toFingerprintRate(input, rate),
                    stream(input, rate),
                    0f
                )
            }
        }
    }

    @Test
    fun `the fingerprint rate passes straight through`() {
        val input = FloatArray(1_000) { sin(it / 10.0).toFloat() }
        assertArrayEquals(input, stream(input, 8_000), 0f)
    }

    @Test
    fun `16-bit stereo is averaged to mono`() {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        bytes.putShort(Short.MAX_VALUE).putShort(0).putShort(-16_384).putShort(-16_384).flip()
        val out = FloatBuffer(4)
        val frames = PcmDownmix.mix(bytes, 2, android.media.AudioFormat.ENCODING_PCM_16BIT, StreamingResampler(8_000, out))
        assertEquals(2, frames)
        assertArrayEquals(floatArrayOf(0.5f, -16_384f / Short.MAX_VALUE), out.toFloatArray(), 1e-6f)
    }

    @Test
    fun `float output is read as float, not as twice as many shorts`() {
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        bytes.putFloat(0.25f).putFloat(0.75f).putFloat(-1f).putFloat(0f).flip()
        val out = FloatBuffer(4)
        val frames = PcmDownmix.mix(bytes, 2, android.media.AudioFormat.ENCODING_PCM_FLOAT, StreamingResampler(8_000, out))
        assertEquals(2, frames)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), out.toFloatArray(), 0f)
    }
}
