package com.wander.android.core.audio.melody

/**
 * Whether searching by a hummed melody is switched on.
 *
 * ## Why it is off
 *
 * The engine is not broken in a way a threshold fixes. Pitch tracking assumes one voice at a time;
 * a hum satisfies that and a finished mix does not, so what gets stored for a dense production is
 * whatever periodicity dominated — usually the bass, which barely moves. Measured across a real
 * library's contours, a quarter of every interval was a jump of eleven semitones or more and
 * roughly a third were no interval at all. One track stored twelve notes for two and three-quarter
 * minutes, ten of them flat.
 *
 * Nothing anybody hums can match a shape like that, and no tuning changes it: it is the wrong
 * signal, not a badly read one. Getting a usable melody out of a mix needs either predominant-melody
 * extraction or, better, separating the vocal first — work that belongs off the phone, and a
 * decision to make deliberately rather than by leaving a feature switched on that cannot deliver.
 *
 * ## What being off means
 *
 * Contours are not measured, which is a quarter of the work of every decode the indexer does.
 * Recognition answers from the landmark engine alone — which is exact, is what actually identifies
 * a recording playing in a room, and is what the interface now promises. Nothing is deleted:
 * flipping this back on restores the whole path, and stored contours from before stay where they
 * are.
 */
internal object MelodySearch {

    /** Set true to bring humming back. See the note above before doing so. */
    const val ENABLED = false
}
