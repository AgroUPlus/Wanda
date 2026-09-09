package com.wander.android.core.audio.fingerprint

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Turns PCM into neural audio-fingerprint vectors, one per one-second segment.
 *
 * The model — `wanda_embedder.tflite`, downloaded on demand by [EmbeddingModelManager] rather
 * than shipped in the APK — is the `nmfp-triplet` encoder from raraz15/neural-music-fp with its
 * mel front-end folded into the graph. It takes raw 8 kHz mono float PCM `(1, 8000)` and emits a
 * 128-d L2-normalised embedding `(1, 128)`. The identical file runs in the desktop indexer
 * (`core/embedder.py`), so a track fingerprinted on a laptop and a clip captured on the phone are
 * directly comparable with no Kotlin/Python parity to maintain — the thing that made the landmark
 * path fragile.
 *
 * One interpreter, created lazily and reused. TFLite interpreters are not thread-safe; callers
 * serialise through [embed], which is `@Synchronized`.
 */
@Singleton
class AudioEmbedder @Inject constructor(
    private val modelManager: EmbeddingModelManager
) {

    private var interpreter: Interpreter? = null

    private fun interpreter(): Interpreter =
        interpreter ?: buildInterpreter().also { interpreter = it }

    /**
     * CPU, deliberately.
     *
     * The graph carries its own mel front-end, and `tf.signal.rfft` is supported by neither the
     * NNAPI nor the GPU delegate. A delegate would therefore take only the convolution stack and
     * pay a host-to-device copy on each of the ~119 invocations a minute of audio costs, which on
     * a model this small is a loss rather than a win. Threads are the lever that does help.
     *
     * Measured cost is logged per call at [TAG]; indexing a streamed track is dominated by the
     * network fetch and the decode, not by this.
     */
    private fun buildInterpreter(): Interpreter {
        // Downloaded to app storage during setup, not shipped in the APK — see [EmbeddingModelManager].
        val file = modelManager.modelFile
        val model: MappedByteBuffer = RandomAccessFile(file, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
        val options = Interpreter.Options().apply {
            numThreads = 2
            setUseXNNPACK(true)
        }
        return Interpreter(model, options)
    }

    /**
     * The segment embeddings for [samples] (mono, 8 kHz, float), segment-major.
     *
     * 1 s windows at a 0.5 s hop, tail zero-padded — matched byte-for-byte to `_segment` in
     * `core/embedder.py`. Returns an empty array only for empty input.
     */
    @Synchronized
    fun embed(samples: FloatArray): Array<FloatArray> {
        if (samples.isEmpty()) return emptyArray()
        val started = SystemClock.elapsedRealtime()
        val windows = segment(samples)
        val itp = interpreter()

        val input = ByteBuffer.allocateDirect(SEGMENT_SAMPLES * 4).order(ByteOrder.nativeOrder())
        val output = Array(1) { FloatArray(EMBED_DIM) }
        val result = Array(windows.size) { FloatArray(EMBED_DIM) }

        for ((i, window) in windows.withIndex()) {
            input.rewind()
            for (v in window) input.putFloat(v)
            input.rewind()
            itp.run(input, output)
            // Re-normalise: fp16 round-off can leave the vector fractionally off the unit sphere,
            // which would bias every later cosine score.
            var norm = 0f
            for (x in output[0]) norm += x * x
            norm = sqrt(norm)
            val row = result[i]
            if (norm > 0f) for (d in 0 until EMBED_DIM) row[d] = output[0][d] / norm
            else output[0].copyInto(row)
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        Log.i(
            TAG,
            "embedded ${windows.size} segments (${samples.size / SEGMENT_SAMPLES}s) in ${elapsed}ms " +
                "(${elapsed / windows.size.coerceAtLeast(1)}ms/segment)"
        )
        return result
    }

    /** Whether the model has been downloaded. Recognition-by-embedding is off until it has. */
    fun isAvailable(): Boolean = modelManager.isReady()

    /**
     * The same fact as [isAvailable], as a flow.
     *
     * Re-exported here rather than injecting [EmbeddingModelManager] into everything that cares:
     * "can this device embed audio" is a question about the embedder, and a download that finishes
     * while the recogniser is open should change what it says without the user reopening it.
     */
    val modelState: StateFlow<EmbeddingModelManager.State> get() = modelManager.state

    private fun segment(samples: FloatArray): Array<FloatArray> {
        var pcm = samples
        if (pcm.size < SEGMENT_SAMPLES) pcm = pcm.copyOf(SEGMENT_SAMPLES)
        var n = 1 + (pcm.size - SEGMENT_SAMPLES) / HOP_SAMPLES
        val remainder = pcm.size - ((n - 1) * HOP_SAMPLES + SEGMENT_SAMPLES)
        if (remainder > 0) {
            pcm = pcm.copyOf(pcm.size + (HOP_SAMPLES - remainder))
            n += 1
        }
        return Array(n) { i -> pcm.copyOfRange(i * HOP_SAMPLES, i * HOP_SAMPLES + SEGMENT_SAMPLES) }
    }

    companion object {
        private const val TAG = "AudioEmbedder"

        const val EMBED_DIM = 128
        const val SEGMENT_SAMPLES = 8_000   // 1.0 s at AudioFormat.SAMPLE_RATE
        const val HOP_SAMPLES = 4_000       // 0.5 s — the model's training fingerprint rate

        /** Identifies which model produced a stored vector; see [com.wander.android.data.repository.EmbeddingRepository]. */
        const val MODEL_NAME = "nmfp-triplet"

        /**
         * Bumped when the model file, [segment], or the storage format changes; invalidates every
         * stored vector.
         *
         * 2 quantised storage to int8 (see [QUANT_SCALE]) and began indexing whole tracks rather
         * than their first minute.
         */
        const val EMBEDDER_VERSION = 2

        /**
         * Fixed-point scale for a stored component: `byte = round(value * 255)`.
         *
         * The model's output is L2-normalised across 128 dimensions, so no single component can be
         * large — measured over 162,447 real segment vectors the extreme was **0.433**, and none
         * came within a third of the 0.498 that would clip at this scale. float32 was therefore
         * spending four bytes to describe a number that lives in a narrow band, which mattered
         * once tracks were indexed whole: 250 MB of vectors instead of 63.
         *
         * Measured against the float32 originals on the same library, quantising both the stored
         * vectors and the query changes a match score by at most 0.002 — against thresholds spaced
         * 0.04 apart — and leaves the separation between the true track and the best impostor
         * identical to four decimal places (+0.4829 against +0.4828). It is also four times less
         * memory to read, which is where a match now spends most of its time.
         */
        const val QUANT_SCALE = 255f

        /** Rounds one unit-sphere component onto the stored int8 scale. */
        fun quantise(value: Float): Byte =
            kotlin.math.round(value * QUANT_SCALE).coerceIn(-127f, 127f).toInt().toByte()

        /**
         * Segment vectors as the stored BLOB: one int8 per component, segment-major.
         *
         * The layout is the contract the desktop indexer writes to (`core/embedder.py`) — a byte
         * per component in the same order, so `blob.size / EMBED_DIM` is the segment count and
         * nothing about the file depends on the machine's byte order any more.
         */
        fun pack(vectors: Array<FloatArray>): ByteArray {
            val out = ByteArray(vectors.size * EMBED_DIM)
            for ((i, row) in vectors.withIndex()) {
                val base = i * EMBED_DIM
                for (d in 0 until EMBED_DIM) out[base + d] = quantise(row[d])
            }
            return out
        }

        /** Unpacks a stored BLOB back into rows of [EMBED_DIM] floats. Inverse of [pack]. */
        fun unpack(blob: ByteArray): Array<FloatArray> {
            val n = blob.size / EMBED_DIM
            return Array(n) { i ->
                FloatArray(EMBED_DIM) { d -> blob[i * EMBED_DIM + d] / QUANT_SCALE }
            }
        }

        /**
         * Segment vectors end to end, quantised, for a caller that wants one array of bytes.
         *
         * The query is quantised the same way the stored vectors are, so both sides of every
         * comparison carry the same rounding — which is the configuration the 0.002 worst-case
         * score deviation above was measured in.
         */
        fun flatten(vectors: Array<FloatArray>): SegmentVectors =
            SegmentVectors(pack(vectors), vectors.size)
    }
}

/**
 * Segment vectors laid end to end, quantised, and how many there are.
 *
 * The matcher's working shape. [values] holds `segments * AudioEmbedder.EMBED_DIM` int8 components
 * and may be longer than that — it is a buffer reused across tracks of different lengths, so its
 * size says nothing about the content and [segments] is the only count to trust.
 *
 * Bytes rather than floats because this is what the inner loop walks: the same vectors take a
 * quarter of the memory and a quarter of the bandwidth, and a dot product of two int8 vectors
 * accumulates exactly in `Int` with no rounding of its own.
 */
class SegmentVectors(val values: ByteArray, val segments: Int)
