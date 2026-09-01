package com.wander.android.core.audio.melody

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A melody as the shape it makes, not the notes it is in.
 *
 * Stored as intervals — how far each note moved from the one before it — and durations. Storing
 * pitches would mean a hum in the wrong key matches nothing, and everyone hums in the wrong key;
 * intervals are the same whether the tune is sung by a bass or whistled two octaves up. That is
 * the whole reason for the representation.
 *
 * Two bytes per note, as the feature was specified: a signed semitone delta and a duration in
 * 100 ms ticks. A four-bar hook is well under 150 bytes, so a library of ten thousand tracks costs
 * around a megabyte and a half — small enough that the contour can live beside the row it
 * describes rather than in a structure of its own.
 */
internal data class MelodyContour(val notes: List<Note>) {

    /** One step of the tune: where it went, and how long it stayed. */
    internal data class Note(
        /** Semitones from the previous note. The first note is always 0 — it has no predecessor. */
        val delta: Int,
        /** Length in 100 ms ticks, at least 1. */
        val ticks: Int
    )

    val size: Int get() = notes.size

    /**
     * Two bytes per note, ready for a BLOB column.
     *
     * Deltas are clamped rather than dropped. A jump wider than an octave and a half is almost
     * always the pitch tracker having slipped an octave, and the clamp keeps that mistake local:
     * one wrong interval instead of a note that vanishes and shifts everything after it.
     */
    fun toBytes(): ByteArray {
        val bytes = ByteArray(notes.size * 2)
        notes.forEachIndexed { index, note ->
            bytes[index * 2] = note.delta.coerceIn(MIN_DELTA, MAX_DELTA).toByte()
            bytes[index * 2 + 1] = note.ticks.coerceIn(1, MAX_TICKS).toByte()
        }
        return bytes
    }

    internal companion object {
        const val MIN_DELTA = -18
        const val MAX_DELTA = 18

        /** A tick is 100 ms, so this caps one note at 25.5 seconds. */
        const val MAX_TICKS = 255

        /** Below this there is not enough shape to identify anything, and a match would be luck. */
        const val MIN_NOTES = 4

        fun fromBytes(bytes: ByteArray): MelodyContour {
            val notes = ArrayList<Note>(bytes.size / 2)
            var index = 0
            while (index + 1 < bytes.size) {
                notes += Note(
                    delta = bytes[index].toInt(),
                    // Unsigned: a note longer than 1.27 s is ordinary and must not read as negative.
                    ticks = bytes[index + 1].toInt() and 0xFF
                )
                index += 2
            }
            return MelodyContour(notes)
        }

        /**
         * Turns a frame-by-frame pitch track into notes.
         *
         * The job is to decide where one note ends and the next begins, from a signal that wobbles
         * continuously. A new note is declared when the pitch settles [NOTE_THRESHOLD] semitones
         * away from where it had settled, and held only if it stays there for [MIN_FRAMES] — which
         * is what stops a scoop into a note, or a moment of vibrato, from being read as two notes
         * of their own.
         *
         * Unvoiced frames end the current note without starting one. A breath is a boundary, and
         * the silence between two phrases is not itself a step in the tune.
         */
        fun fromPitchTrack(pitches: FloatArray, framesPerSecond: Float): MelodyContour {
            val notes = mutableListOf<Note>()
            var currentMidi = 0f
            var frames = 0
            var previousMidi: Float? = null

            fun close() {
                if (frames >= MIN_FRAMES) {
                    val seconds = frames / framesPerSecond
                    val ticks = (seconds * 10f).roundToInt().coerceIn(1, MAX_TICKS)
                    val delta = previousMidi?.let { (currentMidi - it).roundToInt() } ?: 0
                    notes += Note(delta.coerceIn(MIN_DELTA, MAX_DELTA), ticks)
                    previousMidi = currentMidi
                }
                frames = 0
            }

            for (hz in pitches) {
                if (hz <= 0f) {
                    close()
                    continue
                }
                val midi = PitchDetector.midiOf(hz)
                if (frames == 0) {
                    currentMidi = midi
                    frames = 1
                    continue
                }
                if (abs(midi - currentMidi) > NOTE_THRESHOLD) {
                    close()
                    currentMidi = midi
                    frames = 1
                } else {
                    // A running mean, so the stored pitch is the note rather than whichever frame
                    // happened to start it.
                    currentMidi = (currentMidi * frames + midi) / (frames + 1)
                    frames++
                }
            }
            close()
            return MelodyContour(notes)
        }

        /** Nearly a semitone: wide enough to absorb vibrato, narrow enough to catch a real step. */
        private const val NOTE_THRESHOLD = 0.8f

        /**
         * How many consecutive frames make a note. At a 32 ms hop this is about 130 ms — shorter
         * than any note somebody hums, longer than a slide between two.
         */
        private const val MIN_FRAMES = 4
    }
}
