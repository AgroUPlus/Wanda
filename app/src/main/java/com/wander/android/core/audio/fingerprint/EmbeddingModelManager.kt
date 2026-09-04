package com.wander.android.core.audio.fingerprint

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the neural fingerprint model file: whether it is present, and fetching it on request.
 *
 * The model is ~35 MB, which is most of an APK on its own and useless to anyone who never opens
 * the recogniser — so it is not shipped in `assets/`. It is downloaded once, during setup or from
 * Settings, into app storage, and everything that needs it ([AudioEmbedder]) reads it from there.
 * Absent, recognition-by-embedding simply does nothing; the landmark path is unaffected.
 *
 * A successful download is verified two ways and only then does a `.ready` marker get written:
 * the bytes must match a pinned SHA-256, and TFLite must be able to load the file and run one
 * inference. After that, every launch is a marker check — no re-hashing, no re-download. The
 * model is only fetched again if its data is cleared or the app is uninstalled.
 */
@Singleton
class EmbeddingModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    sealed interface State {
        /** Not downloaded. Recognition-by-embedding is off. */
        data object Absent : State
        data class Downloading(val fraction: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    /** `filesDir/models/wanda_embedder.tflite`. */
    val modelFile: File = File(File(context.filesDir, "models").apply { mkdirs() }, FILE_NAME)

    /** Written only after the model is verified; holds the sha it was verified against. */
    private val marker: File = File(modelFile.parentFile, "$FILE_NAME.ready")

    private val _state = MutableStateFlow<State>(
        if (isVerifiedPresent()) State.Ready else State.Absent
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun isReady(): Boolean = _state.value is State.Ready && isVerifiedPresent()

    /**
     * A previous download that passed both checks. Trusted without re-hashing 35 MB on every
     * launch: the marker only exists if [download] wrote it, and it is deleted the moment the
     * model file is replaced or removed.
     */
    private fun isVerifiedPresent(): Boolean =
        modelFile.exists() &&
            marker.exists() &&
            runCatching { marker.readText().trim() == SHA256 }.getOrDefault(false)

    /**
     * Downloads and verifies the model if it is not already usable. Safe to call repeatedly; a
     * call while a download is in flight is ignored.
     */
    suspend fun ensure() {
        if (isReady()) return
        download()
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is State.Downloading) return@withContext
        if (isVerifiedPresent()) { _state.value = State.Ready; return@withContext }

        _state.value = State.Downloading(0f)
        marker.delete()
        val tmp = File(modelFile.parentFile, "$FILE_NAME.part")
        try {
            httpClient.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { r ->
                val body = r.body ?: error("empty response")
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val total = body.contentLength().takeIf { it > 0 } ?: SIZE_BYTES
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            _state.value =
                                State.Downloading((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (hex != SHA256) error("checksum mismatch (got $hex)")
            }

            if (!tmp.renameTo(modelFile)) {
                tmp.copyTo(modelFile, overwrite = true); tmp.delete()
            }

            // The "works now, without a song" check: TFLite loads the file and produces a
            // 128-value embedding for one second of silence.
            val problem = selfCheck()
            if (problem != null) {
                modelFile.delete()
                _state.value = State.Failed(problem)
                return@withContext
            }

            marker.writeText(SHA256)
            _state.value = State.Ready
            Log.i(TAG, "model ready (${modelFile.length()} bytes, self-check passed)")
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "model download failed", e)
            _state.value = State.Failed(e.message ?: "download failed")
        }
    }

    /** Re-runs the load + inference check against the file already on disk. For a Settings button. */
    suspend fun verifyNow(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) { _state.value = State.Absent; return@withContext false }
        val problem = selfCheck()
        if (problem == null) {
            marker.writeText(SHA256)
            _state.value = State.Ready
            true
        } else {
            _state.value = State.Failed(problem)
            false
        }
    }

    /** null on success, else a short reason the model cannot be used. */
    private fun selfCheck(): String? = try {
        val buffer = RandomAccessFile(modelFile, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }
        Interpreter(buffer).use { interp ->
            val input = Array(1) { FloatArray(8_000) }
            val output = Array(1) { FloatArray(128) }
            interp.run(input, output)
            if (output[0].all { it == 0f }) "model produced an empty embedding" else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "model self-check failed", e)
        "model could not be loaded: ${e.message}"
    }

    /** Removes the model. For a Settings "free up space" action. */
    fun delete() {
        modelFile.delete()
        marker.delete()
        _state.value = State.Absent
    }

    companion object {
        private const val TAG = "EmbeddingModel"
        const val FILE_NAME = "wanda_embedder.tflite"

        /**
         * Where the model is hosted. A GitHub release asset, not Git LFS: LFS bandwidth is metered
         * (1 GiB/month on the free tier) and a release asset is not. Update the tag, size and hash
         * together whenever the model is rebuilt (see wanda-indexer `tools/build_embedder_tflite.py`).
         */
        const val MODEL_URL =
            "https://github.com/AgroUPlus/Wanda/releases/download/embedder-v1/wanda_embedder.tflite"
        const val SHA256 = "50a26e9f19dbaa35de7a1efda1c3b69d471e83f860c53c7df184431e754455aa"
        const val SIZE_BYTES = 35_892_012L

        /** ~35 MB, for a UI that warns before spending someone's data. */
        const val APPROX_MB = 34
    }
}
