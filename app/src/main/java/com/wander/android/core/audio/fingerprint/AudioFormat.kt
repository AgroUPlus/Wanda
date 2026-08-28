package com.wander.android.core.audio.fingerprint

/**
 * The one audio shape every part of recognition agrees on.
 *
 * Both sides of a match must be fingerprinted identically or the hashes cannot line up, so the
 * rate, the frame and the hop live here rather than being repeated at the decoder, the microphone
 * and the matcher — three places that would each have been free to drift.
 *
 * 8 kHz is deliberate and is not a quality compromise. Fingerprinting looks at where the strongest
 * partials sit, and for recorded music almost all of that lives below 4 kHz; everything above it
 * is the first thing a room, a phone speaker and a cheap microphone destroy, so carrying it would
 * add cost and *reduce* robustness. It also makes the transform four times cheaper than at 32 kHz,
 * which matters when the index is built over a whole library on a phone.
 */
object AudioFormat {

    /** Mono, 8 kHz. Nyquist is 4 kHz — see the note above on why that is plenty. */
    const val SAMPLE_RATE = 8_000

    /** 128 ms per frame. Long enough to resolve a note, short enough not to smear a beat. */
    const val FRAME_SIZE = 1024

    /**
     * 32 ms between frames — a 75% overlap.
     *
     * Overlap is what makes the fingerprint survive the listener not starting the recording on a
     * frame boundary. With no overlap, a clip captured half a frame out of phase produces a
     * different spectrum for the same music and matches nothing.
     */
    const val HOP_SIZE = 256

    /** Frames per second of fingerprint time. Used to turn frame offsets back into seconds. */
    const val FRAMES_PER_SECOND = SAMPLE_RATE.toFloat() / HOP_SIZE

    /** Usable spectrum: the FFT's second half mirrors the first and carries nothing new. */
    const val BIN_COUNT = FRAME_SIZE / 2
}
