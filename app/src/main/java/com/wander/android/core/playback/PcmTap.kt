package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.wander.android.core.audio.visualizer.AudioFftProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds decoded PCM into the FFT processor for the on-screen visualizer.
 *
 * Only active while the Now Playing screen is visible ([AudioFftProcessor.isVisualizerActive]),
 * and only when audio offload is off — offloaded audio never passes through the processor chain,
 * which is why enabling a visualizer disables offload in [PlayerFactory].
 */
@Singleton
@OptIn(UnstableApi::class)
class PcmTap @Inject constructor(
    private val fftProcessor: AudioFftProcessor
) : TeeAudioProcessor.AudioBufferSink {

    private var samples = ShortArray(2048)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) = Unit

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!fftProcessor.isVisualizerActive) return

        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val count = shorts.remaining()
        if (count == 0) return

        if (samples.size < count) samples = ShortArray(count)
        shorts.get(samples, 0, count)
        fftProcessor.processPcmSamples(samples, count)
    }
}
