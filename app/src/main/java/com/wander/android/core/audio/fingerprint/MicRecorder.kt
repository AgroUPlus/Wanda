package com.wander.android.core.audio.fingerprint

import android.annotation.SuppressLint
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.wander.android.BuildConfig
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

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

    private val _audioLevel = MutableStateFlow(0f)

    /** Instantaneous audio volume level in `[0f, 1f]` updated in real time during capture. */
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isRecording = MutableStateFlow(false)

    /**
     * Whether the microphone is open right now.
     *
     * The sheet's "Listening…" is a claim about the microphone, and it went on being made for the
     * whole time the matcher was working — long after the microphone had closed and the wave had
     * gone flat, which is exactly when it looked broken. This is what lets the UI stop saying it.
     */
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /**
     * Records [seconds] of audio. Caller must hold `RECORD_AUDIO`.
     *
     * Returns null when the recorder could not start — another app holding the microphone, or a
     * device that rejects the configuration. Null rather than an exception because "could not
     * listen" is an outcome the UI has to show either way.
     */
    suspend fun record(seconds: Int): FloatArray? =
        stream(seconds, checkpointSeconds = 0).lastOrNull()

    /**
     * The same capture, handed out as it accumulates.
     *
     * Emits the clip so far every [checkpointSeconds], and the whole clip at the end — each
     * emission a complete, resampled array starting from the beginning, not a delta, so a consumer
     * can simply try to recognise each one. Zero [checkpointSeconds] emits only the final clip.
     *
     * ## Why this is a flow and not a callback
     *
     * The point is to answer as soon as the answer is certain instead of always waiting out the
     * full six seconds. That means matching *while* recording, and a matcher invoked inline would
     * stall the read loop for as long as it took — `AudioRecord`'s buffer is a fraction of a
     * second, so the audio arriving during the match would simply be dropped, and the clip that
     * finally got matched would have a hole in it.
     *
     * `CONFLATED` buffering is what keeps them apart: the recorder never waits for the consumer,
     * and a consumer that was busy through two checkpoints resumes on the newest clip rather than
     * working through a backlog of stale prefixes it no longer cares about. Cancelling collection
     * — which is what a caller does the moment it is sure — closes the microphone.
     */
    fun stream(seconds: Int, checkpointSeconds: Int): Flow<FloatArray> =
        capture(seconds, checkpointSeconds)
            .flowOn(Dispatchers.IO)
            .buffer(Channel.CONFLATED)

    @SuppressLint("MissingPermission")
    private fun capture(seconds: Int, checkpointSeconds: Int): Flow<FloatArray> = flow {
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
        // Read in responsive ~45ms slices (approx 2048 shorts) for fluid UI level metering
        // while the recorder's internal buffer remains large to prevent dropped frames.
        val stepSize = maxOf(minBuffer, 2048)
        val chunk = ShortArray(stepSize)
        var written = 0
        val checkpointEvery = if (checkpointSeconds > 0) RECORD_RATE * checkpointSeconds else 0
        var nextCheckpoint = checkpointEvery
        var level = 0f

        try {
            recorder.startRecording()
            _isRecording.value = true
            while (written < total) {
                // Cancellation is checked every chunk, so dismissing the sheet stops the
                // microphone within a fraction of a second rather than at the end of the clip.
                coroutineContext.ensureActive()
                val read = recorder.read(chunk, 0, minOf(chunk.size, total - written))
                if (read <= 0) break
                var sumSquares = 0f
                for (i in 0 until read) {
                    val s = chunk[i] / Short.MAX_VALUE.toFloat()
                    samples[written + i] = s
                    sumSquares += s * s
                }
                written += read
                level = follow(level, loudness(sqrt(sumSquares / read)))
                _audioLevel.value = level

                if (checkpointEvery > 0 && written >= nextCheckpoint && written < total) {
                    nextCheckpoint += checkpointEvery
                    emit(Resampler.toFingerprintRate(samples.copyOf(written), RECORD_RATE))
                }
            }
        } catch (e: IllegalStateException) {
            return@flow
        } finally {
            _isRecording.value = false
            _audioLevel.value = 0f
            runCatching { recorder.stop() }
            recorder.release()
        }

        if (written < RECORD_RATE) return@flow
        emit(
            Resampler.toFingerprintRate(samples.copyOf(written), RECORD_RATE)
                .also { if (BuildConfig.DEBUG) dumpForDiagnosis(it) }
        )
    }

    /**
     * RMS as something an eye can see.
     *
     * The level drove a wave that barely moved, because it was the raw RMS times a constant and
     * hearing is not linear. A room with music playing in it measured 0.0028 RMS on this device —
     * `rms * 4.5` is 0.013, which is visually indistinguishable from silence, while the same clip
     * peaked at 0.117. Loud passages then clipped at the top of a range nothing else ever reached.
     *
     * Decibels instead, over a [FLOOR_DB] window, which is how the signal is actually distributed:
     * quiet music lands near the middle of the bar rather than against the bottom of it, and the
     * whole range gets used.
     */
    private fun loudness(rms: Float): Float {
        if (rms <= 0f) return 0f
        val db = 20f * log10(rms)
        return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
    }

    /**
     * Rises fast, falls slow.
     *
     * A beat is an attack and a tail, and following both at the same speed reads as either
     * sluggish (slow enough to be smooth) or jittery (fast enough to be prompt). Snapping to a
     * rise and easing off a fall is what makes a level meter look like it is listening — the same
     * asymmetry every VU meter has.
     */
    private fun follow(current: Float, target: Float): Float =
        if (target > current) current + (target - current) * ATTACK
        else current + (target - current) * RELEASE


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

        /** Quietest level the meter shows apart from silence. -60 dBFS is a very quiet room. */
        const val FLOOR_DB = -60f

        /** Per ~46 ms chunk. See [follow]. */
        const val ATTACK = 0.6f
        const val RELEASE = 0.18f

        private const val TAG = "MicRecorder"

        /** In order of preference. See [openRecorder] for why `MIC` leads. */
        val SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT
        )
    }
}
