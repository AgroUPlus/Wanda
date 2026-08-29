package com.wander.android.data.repository

/**
 * The pairs of tracks a user has declared to be different performances.
 *
 * Deliberately a plain value with no Room, Hilt or Android in it. [TrackDeduplicator] is a
 * stateless object and stays one — identity is passed *in* rather than fetched — because that is
 * what keeps the matcher's rules testable against fixtures rather than against a database.
 *
 * Pairs are canonicalised on the way in, so callers never have to sort ids to ask a question.
 */
@JvmInline
value class SplitSet(private val pairs: Set<Pair<String, String>>) {

    /** Whether these two ids have been pinned apart. Order does not matter. */
    fun isApart(a: String, b: String): Boolean = canonical(a, b) in pairs

    val size: Int get() = pairs.size

    /** Every pinned pair, canonically ordered. What a list of pins to undo is built from. */
    val all: Set<Pair<String, String>> get() = pairs

    companion object {
        val EMPTY = SplitSet(emptySet())

        /** Builds a set from unordered pairs, canonicalising each one. */
        fun of(pairs: Iterable<Pair<String, String>>): SplitSet =
            SplitSet(pairs.map { (a, b) -> canonical(a, b) }.toSet())

        /** Smaller id first, so one pair has exactly one representation. */
        fun canonical(a: String, b: String): Pair<String, String> =
            if (a <= b) a to b else b to a
    }
}
