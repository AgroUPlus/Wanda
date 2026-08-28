package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.Fft
import com.wander.android.core.audio.fingerprint.Fingerprinter
import com.wander.android.core.audio.fingerprint.HashPacking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The fingerprinter has no visible failure mode.
 *
 * A subtly wrong transform still produces plausible-looking landmarks; it simply never matches
 * anything, and from the outside that is indistinguishable from "the song is not in your library"
 * — the answer the feature legitimately gives most often. So the properties that matching depends
 * on are asserted here rather than trusted.
 */
class FingerprinterTest {

    /** A pure tone lands in the bin its frequency belongs to, and not a neighbouring one. */
    @Test
    fun `fft finds the bin of a pure tone`() {
        val size = 1024
        val binWidth = AudioFormat.SAMPLE_RATE.toDouble() / size
        val targetBin = 64
        val frequency = targetBin * binWidth

        val real = FloatArray(size) { i ->
            sin(2.0 * PI * frequency * i / AudioFormat.SAMPLE_RATE).toFloat()
        }
        val imag = FloatArray(size)
        Fft.transform(real, imag)

        var loudest = 0
        var loudestMagnitude = 0.0
        for (bin in 1 until size / 2) {
            val magnitude = real[bin] * real[bin].toDouble() + imag[bin] * imag[bin].toDouble()
            if (magnitude > loudestMagnitude) {
                loudestMagnitude = magnitude
                loudest = bin
            }
        }
        assertEquals(targetBin, loudest)
    }

    /** Round-tripping the packing recovers what went in, at the resolution it promises. */
    @Test
    fun `hash packing is stable and quantised`() {
        val a = HashPacking.pack(anchorBin = 100, targetBin = 200, deltaFrames = 20)
        val b = HashPacking.pack(anchorBin = 100, targetBin = 200, deltaFrames = 20)
        assertEquals(a, b)

        // Neighbouring bins collapse together: the tolerance that lets a microphone match a file.
        assertEquals(
            HashPacking.pack(100, 200, 20),
            HashPacking.pack(101, 200, 20)
        )
        // A different gap is a different hash, or the time structure would carry no information.
        assertTrue(HashPacking.pack(100, 200, 20) != HashPacking.pack(100, 200, 21))
    }

    /**
     * The property the whole feature rests on: a noisy, quieter excerpt of a recording produces
     * landmarks that the original also produced.
     *
     * This is the microphone, in miniature — the room attenuates, adds noise, and starts partway
     * through. If a shared offset does not survive that, nothing will ever be recognised.
     */
    @Test
    fun `a noisy excerpt shares landmarks with the original at one offset`() {
        val fingerprinter = Fingerprinter()
        val random = Random(seed = 42)
        val seconds = 12
        val total = AudioFormat.SAMPLE_RATE * seconds

        // A plucked sequence rather than a held chord: notes that start, ring and decay. Music
        // is mostly transients, and a fingerprint keyed on where energy *arrives* has nothing to
        // grip on a signal that never changes. A held chord is also the degenerate case — every
        // recording of the same three tones would match it.
        val pitches = doubleArrayOf(220.0, 277.2, 329.6, 440.0, 392.0, 293.7, 246.9, 523.3)
        val noteSeconds = 0.4
        val original = FloatArray(total) { i ->
            val t = i.toDouble() / AudioFormat.SAMPLE_RATE
            val noteIndex = (t / noteSeconds).toInt()
            val intoNote = t - noteIndex * noteSeconds
            val pitch = pitches[noteIndex % pitches.size]
            // Exponential decay from the attack, plus a quieter octave for spectral structure.
            val envelope = Math.exp(-6.0 * intoNote)
            ((sin(2 * PI * pitch * t) + 0.4 * sin(2 * PI * pitch * 2 * t)) * envelope).toFloat()
        }

        val startFrame = 120
        val startSample = startFrame * AudioFormat.HOP_SIZE
        val excerptLength = AudioFormat.SAMPLE_RATE * 5
        val excerpt = FloatArray(excerptLength) { i ->
            // Half the level, plus noise: a phone across a room.
            original[startSample + i] * 0.5f + (random.nextFloat() - 0.5f) * 0.05f
        }

        val indexed = fingerprinter.fingerprint(original)
        val heard = fingerprinter.fingerprint(excerpt)
        assertTrue("the original produced no landmarks", indexed.isNotEmpty())
        assertTrue("the excerpt produced no landmarks", heard.isNotEmpty())

        // Exactly what RecognitionRepository does: bin the shared hashes by time offset and look
        // for one offset that dominates.
        val byHash = indexed.groupBy({ it.hash.value }, { it.anchorFrame })
        val votes = HashMap<Int, Int>()
        for (landmark in heard) {
            for (indexedFrame in byHash[landmark.hash.value].orEmpty()) {
                val delta = indexedFrame - landmark.anchorFrame
                votes[delta] = (votes[delta] ?: 0) + 1
            }
        }

        val best = votes.maxByOrNull { it.value }
        assertTrue("no shared landmarks at all", best != null)
        // The winning offset must be where the excerpt was actually cut from, within the slack
        // peak picking allows.
        assertTrue(
            "expected an offset near $startFrame, got ${best!!.key}",
            abs(best.key - startFrame) <= 2
        )
        assertTrue("alignment too weak to be a match: ${best.value}", best.value >= 12)
    }

    /**
     * One `Fingerprinter` used from several threads at once gives each of them the same answer it
     * would have given alone.
     *
     * Not a hypothetical. `RecognitionRepository` is a singleton holding a single `Fingerprinter`,
     * and it is called from both the indexing worker and the listening sheet — so the two really
     * do overlap whenever a library index is running while someone taps the note. The class used
     * to keep its FFT scratch buffers as instance state, which meant each thread was writing into
     * the other's transform. Nothing crashed; the fingerprints were simply wrong, and a wrong
     * fingerprint is indistinguishable from "that song is not in your library".
     */
    @Test
    fun `one fingerprinter is safe to share between threads`() {
        val fingerprinter = Fingerprinter()
        val samples = FloatArray(AudioFormat.SAMPLE_RATE * 4) { i ->
            val t = i.toDouble() / AudioFormat.SAMPLE_RATE
            (sin(2 * PI * 330 * t) * Math.exp(-3.0 * (t % 0.3))).toFloat()
        }

        val expected = fingerprinter.fingerprint(samples)
        assertTrue("no landmarks to compare", expected.isNotEmpty())

        val threads = 4
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = pool.invokeAll(
                (1..threads).map { Callable { fingerprinter.fingerprint(samples) } }
            ).map { it.get(30, TimeUnit.SECONDS) }

            for ((index, result) in results.withIndex()) {
                assertEquals("thread $index disagreed", expected, result)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
