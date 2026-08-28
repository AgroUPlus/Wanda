package com.wander.android.core.audio.fingerprint

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.min

/**
 * Decodes a local audio file to the mono 8 kHz float PCM the fingerprinter expects.
 *
 * `MediaCodec` rather than a library: the platform already ships decoders for everything the app
 * can play, they are hardware-accelerated, and indexing a library is exactly the workload where
 * that matters.
 *
 * Only local files. A streaming source would have to be downloaded in full to be fingerprinted,
 * and fingerprinting a Navidrome library by pulling every track over the network is a different
 * feature with a different cost — see `RecognitionIndexer`.
 */
class PcmDecoder @Inject constructor() {

    /**
     * Decodes at most [maxSeconds] of [path], from the start.
     *
     * Truncated rather than whole, and this is a judgement about what a fingerprint is for: a
     * minute of a song is thousands of landmarks, enough to identify it many times over, and
     * indexing the remaining three minutes triples the database to make an already-certain match
     * more certain. Null when the file holds no audio track this device can decode.
     */
    fun decode(path: String, maxSeconds: Int = DEFAULT_MAX_SECONDS): FloatArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            decodeTrack(extractor, format, maxSeconds)
        } catch (e: java.io.IOException) {
            // An unreadable file is a fact about the file, not an error worth propagating: the
            // indexer simply has nothing to add for it and moves on to the next one.
            null
        } catch (e: IllegalArgumentException) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun decodeTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        maxSeconds: Int
    ): FloatArray? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        // A primitive buffer, not `ArrayList<Float>`. The list boxed every sample — around 2.6
        // million objects for a single minute of 44.1 kHz audio — and indexing a library is
        // thousands of tracks of that, which is GC pressure measured in whole seconds of CPU.
        val mono = FloatBuffer(sourceRate * maxSeconds)
        val wanted = sourceRate.toLong() * maxSeconds

        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            var framesRead = 0L

            while (!sawOutputEnd && framesRead < wanted) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        framesRead += downmix(shorts, channels, mono)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                }
            }
        } catch (e: MediaCodec.CodecException) {
            return null
        } catch (e: IllegalStateException) {
            return null
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }

        if (mono.isEmpty) return null
        return Resampler.toFingerprintRate(mono.toFloatArray(), sourceRate)
    }

    /** Averages channels down to one, appending to [into]. Returns the frames written. */
    private fun downmix(shorts: java.nio.ShortBuffer, channels: Int, into: FloatBuffer): Int {
        val frames = shorts.remaining() / channels
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += shorts.get(frame * channels + channel) / Short.MAX_VALUE.toFloat()
            }
            into.add(sum / channels)
        }
        return frames
    }

    private companion object {
        const val TIMEOUT_US = 10_000L

        /** See [decode]: enough to identify a record several times over. */
        const val DEFAULT_MAX_SECONDS = 60
    }
}

/**
 * A growable `FloatArray`.
 *
 * `ArrayList<Float>` is the obvious thing to reach for and is the wrong one here: it stores boxed
 * `java.lang.Float` objects, and the decoder appends one per sample per channel-frame. Kotlin has
 * no primitive list in its standard library, so this is fifteen lines rather than a dependency.
 */
private class FloatBuffer(initialCapacity: Int) {

    private var data = FloatArray(initialCapacity.coerceAtLeast(1))
    private var size = 0

    val isEmpty: Boolean get() = size == 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    /** A right-sized copy: the caller keeps this for the life of the fingerprint. */
    fun toFloatArray(): FloatArray = data.copyOf(size)
}

/** Linear resampling to [AudioFormat.SAMPLE_RATE]. */
internal object Resampler {

    /**
     * Linear interpolation, not a windowed-sinc filter.
     *
     * Downsampling this way aliases anything above the new Nyquist back into the band. That is a
     * real defect and it is tolerable here for one reason: *both* sides of a match go through this
     * same function, so the artefacts land in the same places in the file and in the microphone
     * clip, and the peaks still line up. A proper filter would be strictly better audio and no
     * better matching.
     */
    fun toFingerprintRate(samples: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == AudioFormat.SAMPLE_RATE) return samples
        val ratio = sourceRate.toDouble() / AudioFormat.SAMPLE_RATE
        val outputSize = (samples.size / ratio).toInt()
        if (outputSize <= 0) return FloatArray(0)
        return FloatArray(outputSize) { i ->
            val position = i * ratio
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = samples[index]
            val b = samples[min(index + 1, samples.lastIndex)]
            a + (b - a) * fraction
        }
    }
}
