package com.wander.android.core.audio.fingerprint

import android.media.AudioFormat as PlatformAudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import javax.inject.Inject

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
     * Truncated rather than whole, because decoding a whole library is paid per second of audio.
     *
     * **What truncation does not buy is coverage.** The first minute of a song is thousands of
     * landmarks, which is ample density — but density is not the thing recognition needs. A clip
     * is taken from wherever the listener happens to be standing, and a clip from the third minute
     * of a song whose first minute is indexed shares no landmarks with it at all. No amount of
     * detail in the indexed part helps; the answer is simply absent. That is why [startSeconds]
     * exists, and why the indexer takes several windows spread across a track rather than one run
     * at its head.
     *
     * Null when the source holds no audio track this device can decode.
     *
     * [path] is a local file, a `content://` URI, **or an `http(s)` URL**. The truncation is what
     * makes the last one affordable: `MediaExtractor` reads a remote source in ranges and stops
     * where this stops, so indexing a streamed track costs about a minute of audio rather than a
     * download. [headers] carries the authorisation a private server needs — without it a
     * Navidrome URL answers 401 and nothing is indexed, silently.
     */
    fun decode(
        path: String,
        headers: Map<String, String> = emptyMap(),
        maxSeconds: Int = DEFAULT_MAX_SECONDS,
        startSeconds: Int = 0
    ): FloatArray? {
        val extractor = MediaExtractor()
        return try {
            if (headers.isEmpty()) {
                extractor.setDataSource(path)
            } else {
                extractor.setDataSource(path, headers)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            if (startSeconds > 0) {
                // `SEEK_TO_CLOSEST_SYNC` rather than an exact seek: landing on a keyframe is what
                // lets the decoder produce valid output immediately, and being a fraction of a
                // second out is invisible to a fingerprint, whose alignment is established by the
                // match rather than assumed from the seek.
                extractor.seekTo(
                    startSeconds.toLong() * 1_000_000L,
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC
                )
            }
            decodeTrack(extractor, format, maxSeconds)
        } catch (e: java.io.IOException) {
            // An unreadable file is a fact about the file, not an error worth propagating: the
            // indexer records the attempt, backs off, and moves on to the next one. Logged by
            // class only — the message can carry the path or the stream URL.
            Log.w(TAG, "Could not open source: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not open source: ${e.javaClass.simpleName}")
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
        // The container's description, until the codec says otherwise. It often does: HE-AAC
        // declares half its real rate (the SBR layer doubles it) and parametric stereo declares
        // one channel and decodes to two, so trusting the input format played those tracks into
        // the fingerprint at the wrong speed, or read every frame as two.
        var shape = OutputShape(
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            encoding = pcmEncodingOf(format, PlatformAudioFormat.ENCODING_PCM_16BIT)
        )

        val codec = MediaCodec.createDecoderByType(mime)
        // A primitive buffer, at the fingerprint rate, filled as the decode runs. Samples are
        // downmixed and resampled the moment they leave the codec, so the decode never holds
        // source-rate audio: at 44.1 kHz stereo that was 5.5 times the floats kept, 127 MB at the
        // twelve-minute embedding bound, all of it garbage the moment the resample finished.
        //
        // Sized from the *track*, bounded by [maxSeconds], rather than from [maxSeconds] alone,
        // so a three-minute song does not reserve twelve minutes of buffer.
        val out = FloatBuffer(AudioFormat.SAMPLE_RATE * expectedSeconds(format, maxSeconds))
        var resampler = StreamingResampler(shape.rate, out)
        val wanted = AudioFormat.SAMPLE_RATE.toLong() * maxSeconds

        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false

            while (!sawOutputEnd && out.size < wanted) {
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
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val next = OutputShape.of(codec.outputFormat, shape)
                    if (!PcmDownmix.supports(next.encoding) || next.channels <= 0 || next.rate <= 0) {
                        Log.w(TAG, "Unsupported decoder output: encoding ${next.encoding}")
                        return null
                    }
                    if (next.rate != shape.rate) {
                        resampler.finish()
                        resampler = StreamingResampler(next.rate, out)
                    }
                    shape = next
                } else if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        PcmDownmix.mix(buffer, shape.channels, shape.encoding, resampler)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                }
            }
            resampler.finish()
        } catch (e: IllegalStateException) {
            // Includes MediaCodec.CodecException. Logged by class: the codec gave up on this
            // stream, and the caller records the attempt and backs off.
            Log.w(TAG, "Decoder failed: ${e.javaClass.simpleName}")
            return null
        } finally {
            stopAndRelease(codec)
        }

        if (out.isEmpty) return null
        return out.toFloatArray()
    }

    /**
     * Releases [codec] whatever state it ended in.
     *
     * `stop()` throws `IllegalStateException` from the error state a failed decode leaves behind;
     * that is expected there, and `release()` must still run or the codec instance leaks — and the
     * device has only a handful, shared with playback.
     */
    private fun stopAndRelease(codec: MediaCodec) {
        try {
            codec.stop()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Codec stop after failure: ${e.javaClass.simpleName}")
        } finally {
            codec.release()
        }
    }

    /**
     * How many seconds this decode will actually produce, for sizing the buffer.
     *
     * The container's declared duration where it has one, clamped to what was asked for. A stream
     * that declares nothing gets a minute, which grows if it turns out to be longer — one doubling
     * is far cheaper than reserving for the worst case every time.
     */
    private fun expectedSeconds(format: MediaFormat, maxSeconds: Int): Int {
        val declared = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            (format.getLong(MediaFormat.KEY_DURATION) / 1_000_000L).toInt()
        } else {
            0
        }
        if (declared <= 0) return minOf(UNDECLARED_SECONDS, maxSeconds).coerceAtLeast(1)
        // A second of slack: a container's duration and what decodes out of it routinely disagree
        // by a frame or two, and one doubling to hold the difference costs the whole buffer again.
        return minOf(declared + 1, maxSeconds).coerceAtLeast(1)
    }

    /** What the codec is producing: the shape [PcmDownmix] reads each output buffer in. */
    private data class OutputShape(val rate: Int, val channels: Int, val encoding: Int) {
        companion object {
            /** [format]'s values, keeping [previous]'s for any key it leaves out. */
            fun of(format: MediaFormat, previous: OutputShape) = OutputShape(
                rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    previous.rate
                },
                channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    previous.channels
                },
                encoding = pcmEncodingOf(format, previous.encoding)
            )
        }
    }

    companion object {
        private const val TAG = "PcmDecoder"
        private const val TIMEOUT_US = 10_000L

        /** Buffer to reserve for a source that declares no duration. See [expectedSeconds]. */
        private const val UNDECLARED_SECONDS = 60

        /** See [decode]. A window's worth, not a whole track. */
        const val DEFAULT_MAX_SECONDS = 60

        /** A decoder that names no encoding is producing the 16-bit default. */
        private fun pcmEncodingOf(format: MediaFormat, fallback: Int): Int =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                fallback
            }
    }
}
