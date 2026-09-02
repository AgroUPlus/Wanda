package com.wander.android.core.audio.fingerprint

import android.annotation.SuppressLint
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Records a few seconds from the microphone as mono float PCM at the fingerprint rate.
 *
 * Recorded at 44.1 kHz and resampled rather than asking `AudioRecord` for 8 kHz directly: 44.1 is
 * the one rate every Android device is required to support for capture, and a device that quietly
 * refuses the rate you asked for hands back a recorder that initialises and then produces nothing.
 *
 * `UNPROCESSED` where the device offers it. The default `MIC` source runs noise suppression and
 * automatic gain control tuned for speech — both of which attack exactly the sustained tones a
 * music fingerprint is made of.
 */
class MicRecorder @Inject constructor() {

    /**
     * Records [seconds] of audio. Caller must hold `RECORD_AUDIO`.
     *
     * Returns null when the recorder could not start — another app holding the microphone, or a
     * device that rejects the configuration. Null rather than an exception because "could not
     * listen" is an outcome the UI has to show either way.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Int): FloatArray? = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            RECORD_RATE,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return@withContext null

        val bufferSize = maxOf(minBuffer, RECORD_RATE / 2)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED,
                RECORD_RATE,
                AndroidAudioFormat.CHANNEL_IN_MONO,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RECORD_RATE,
                    AndroidAudioFormat.CHANNEL_IN_MONO,
                    AndroidAudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
        } catch (e: IllegalArgumentException) {
            return@withContext null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }

        val total = RECORD_RATE * seconds
        val samples = FloatArray(total)
        val chunk = ShortArray(bufferSize)
        var written = 0

        try {
            recorder.startRecording()
            while (written < total) {
                // Cancellation is checked every chunk, so dismissing the sheet stops the
                // microphone within a fraction of a second rather than at the end of the clip.
                coroutineContext.ensureActive()
                val read = recorder.read(chunk, 0, minOf(chunk.size, total - written))
                if (read <= 0) break
                for (i in 0 until read) {
                    samples[written + i] = chunk[i] / Short.MAX_VALUE.toFloat()
                }
                written += read
            }
        } catch (e: IllegalStateException) {
            return@withContext null
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        if (written < RECORD_RATE) return@withContext null
        Resampler.toFingerprintRate(samples.copyOf(written), RECORD_RATE)
    }

    /**
     * Records [seconds] of audio, handing out everything captured so far as it goes.
     *
     * The same capture as [record] — the microphone is opened once and read in the same loop — but
     * the caller is given the accumulated clip roughly every [EMIT_INTERVAL_MS] instead of only at
     * the end. That is what lets recognition narrow its answer while the audio is still arriving,
     * rather than sitting on a spinner and producing a verdict from nowhere.
     *
     * Each emission is the whole clip from the start, not the newest slice: the matcher aligns a
     * clip against a track, and a one-second fragment on its own carries far too few landmarks to
     * align with anything. Growing prefixes are what make the ranking improve rather than jitter.
     *
     * The flow simply ends when the microphone could not start, which the caller reads the same
     * way [record] returning null reads.
     */
    @SuppressLint("MissingPermission")
    fun recordProgressively(seconds: Int): Flow<FloatArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            RECORD_RATE,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return@flow

        val bufferSize = maxOf(minBuffer, RECORD_RATE / 2)
        val recorder = openRecorder(bufferSize) ?: return@flow

        val total = RECORD_RATE * seconds
        val samples = FloatArray(total)
        val chunk = ShortArray(bufferSize)
        var written = 0
        var lastEmit = 0L

        try {
            recorder.startRecording()
            val startedAt = System.currentTimeMillis()
            while (written < total) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(chunk, 0, minOf(chunk.size, total - written))
                if (read <= 0) break
                for (i in 0 until read) {
                    samples[written + i] = chunk[i] / Short.MAX_VALUE.toFloat()
                }
                written += read

                val elapsed = System.currentTimeMillis() - startedAt
                // Nothing is emitted before there is a second of audio: below that the landmark
                // pass has almost nothing to align and the ranking would be noise presented as
                // deliberation, which is the dishonesty this whole path exists to avoid.
                if (written >= RECORD_RATE && elapsed - lastEmit >= EMIT_INTERVAL_MS) {
                    lastEmit = elapsed
                    emit(Resampler.toFingerprintRate(samples.copyOf(written), RECORD_RATE))
                }
            }
        } catch (e: IllegalStateException) {
            return@flow
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        // The final clip always goes out, even if the last interval had not elapsed: it is the one
        // the verdict is taken from.
        if (written >= RECORD_RATE) {
            emit(Resampler.toFingerprintRate(samples.copyOf(written), RECORD_RATE))
        }
    }.flowOn(Dispatchers.IO)

    /** The two source preferences, shared by both capture paths. */
    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord? {
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED,
                RECORD_RATE,
                AndroidAudioFormat.CHANNEL_IN_MONO,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RECORD_RATE,
                    AndroidAudioFormat.CHANNEL_IN_MONO,
                    AndroidAudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return recorder
    }

    private companion object {
        /** The one capture rate every Android device must support. See the class comment. */
        const val RECORD_RATE = 44_100

        /**
         * How often the growing clip is handed out.
         *
         * A second is long enough that each pass has meaningfully more to work with than the last
         * — the ranking moves for a reason — and slow enough that re-scoring against an index of
         * over a million landmarks is not run harder than it needs to be.
         */
        const val EMIT_INTERVAL_MS = 1_000L
    }
}
