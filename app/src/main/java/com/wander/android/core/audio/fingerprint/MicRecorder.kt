package com.wander.android.core.audio.fingerprint

import android.annotation.SuppressLint
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.wander.android.BuildConfig
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
class MicRecorder @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

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
        val recorder = openRecorder(bufferSize) ?: return@withContext null

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
            .also { if (BuildConfig.DEBUG) dumpForDiagnosis(it) }
    }

    /**
     * Writes the captured clip to the app's files directory, in debug builds only.
     *
     * Recognition failing on a real room is the one thing that cannot be reasoned about from the
     * outside: the clip is gone the moment the match is decided, so "it did not find it" could
     * mean the microphone heard nothing, heard the wrong thing, or heard correctly and the matcher
     * failed. This makes the actual capture available to look at, which turns three guesses into
     * one measurement.
     *
     * Raw 32-bit float mono at the fingerprint rate — the exact array the matcher was given, not a
     * re-encoding of it, because a re-encoding would be a different question.
     */
    private fun dumpForDiagnosis(clip: FloatArray) {
        runCatching {
            val dir = java.io.File(context.filesDir, "capture").apply { mkdirs() }
            val out = java.io.File(dir, "last-listen.f32")
            java.io.DataOutputStream(out.outputStream().buffered()).use { sink ->
                // Little-endian, so anything reading it can treat the file as a plain float array.
                val buffer = java.nio.ByteBuffer.allocate(clip.size * 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                clip.forEach { buffer.putFloat(it) }
                sink.write(buffer.array())
            }
            val rms = kotlin.math.sqrt(clip.fold(0.0) { acc, v -> acc + v * v } / clip.size)
            android.util.Log.i(
                TAG,
                "Captured ${clip.size} samples, rms=%.5f peak=%.5f -> ${out.path}".format(
                    rms,
                    clip.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
                )
            )
        }
    }

    /** The two source preferences, in order. */
    /**
     * Opens the microphone, preferring the source that actually hears music.
     *
     * `MIC` first, not `UNPROCESSED`. On paper `UNPROCESSED` is the better choice — no automatic
     * gain, no noise suppression, nothing between the capsule and the samples, so nothing that can
     * distort what the matcher is about to measure. Measured on a Pixel with a phone playing music
     * beside it, it is the wrong one by a wide margin: the clip came back at about -54 dBFS with
     * its energy in the 2-4 kHz hiss, and the track being played ranked 23rd against its own index
     * entry. The same passage read from the file ranks first by a factor of a hundred.
     *
     * The reason is that `UNPROCESSED` also opts out of the device's microphone *selection* and
     * gain staging, and on a multi-mic phone that can hand back a reference capsule pointing away
     * from whatever you are holding the phone towards. Automatic gain is not the enemy here:
     * peak picking works on log magnitude against a decaying threshold, so it is already
     * indifferent to level — what it cannot survive is the signal not being there.
     */
    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord? {
        for (source in SOURCES) {
            val recorder = try {
                AudioRecord(
                    source,
                    RECORD_RATE,
                    AndroidAudioFormat.CHANNEL_IN_MONO,
                    AndroidAudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                android.util.Log.i(TAG, "Recording from audio source $source")
                return recorder
            }
            recorder.release()
        }
        return null
    }

    private companion object {
        /** The one capture rate every Android device must support. See the class comment. */
        const val RECORD_RATE = 44_100

        private const val TAG = "MicRecorder"

        /** In order of preference. See [openRecorder] for why `MIC` leads. */
        val SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT
        )
    }
}
