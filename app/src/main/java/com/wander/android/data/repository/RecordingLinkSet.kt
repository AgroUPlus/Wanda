package com.wander.android.data.repository

/**
 * The pairs of tracks the fingerprinter has found to hold the same audio.
 *
 * The positive counterpart to [SplitSet], and deliberately the same shape: a plain value with no
 * Room, Hilt or Android in it, passed *into* [TrackDeduplicator] rather than fetched by it. That is
 * what keeps the matcher's rules testable against fixtures rather than against a database.
 *
 * A link is evidence about the audio, so it outranks the metadata rules — two rows whose tags agree
 * about nothing are still one recording if the samples say so. It does not outrank a split: the
 * user's veto is the last word, and a fingerprint match on a mislabelled file is exactly the case
 * where they might exercise it.
 *
 * Pairs are canonicalised on the way in, so callers never have to sort ids to ask a question.
 */
@JvmInline
value class RecordingLinkSet(private val pairs: Set<Pair<String, String>>) {

    /** Whether these two ids have been found to hold the same recording. Order does not matter. */
    fun isLinked(a: String, b: String): Boolean = SplitSet.canonical(a, b) in pairs

    val size: Int get() = pairs.size

    /** Every linked pair, canonically ordered. */
    val all: Set<Pair<String, String>> get() = pairs

    companion object {
        val EMPTY = RecordingLinkSet(emptySet())

        /** Builds a set from unordered pairs, canonicalising each one. */
        fun of(pairs: Iterable<Pair<String, String>>): RecordingLinkSet =
            RecordingLinkSet(pairs.map { (a, b) -> SplitSet.canonical(a, b) }.toSet())
    }
}
