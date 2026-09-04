package com.wander.android.core.audio.fingerprint

import android.os.SystemClock
import android.util.Log
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
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
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

        /** Bumped when the model file or [segment] changes; invalidates every stored vector. */
        const val EMBEDDER_VERSION = 1

        /** Packs a big-endian float32 BLOB back into rows of [EMBED_DIM]. Inverse of the desktop writer. */
        fun unpack(blob: ByteArray): Array<FloatArray> {
            val buf = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)
            val n = blob.size / (EMBED_DIM * 4)
            return Array(n) { FloatArray(EMBED_DIM) { buf.float } }
        }

        /** Serialises segment vectors as a big-endian float32 BLOB. Matches `core/embedder.py`. */
        fun pack(vectors: Array<FloatArray>): ByteArray {
            val buf = ByteBuffer.allocate(vectors.size * EMBED_DIM * 4).order(ByteOrder.BIG_ENDIAN)
            for (row in vectors) for (v in row) buf.putFloat(v)
            return buf.array()
        }
    }
}
