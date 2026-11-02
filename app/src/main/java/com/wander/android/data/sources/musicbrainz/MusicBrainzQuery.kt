package com.wander.android.data.sources.musicbrainz

/**
 * Builds MusicBrainz search queries, which are Lucene syntax.
 *
 * A name is user data, not syntax: an artist called `"Weird Al" Yankovic` closed the quoted phrase
 * early and sent a malformed query, and names such as `AC/DC`, `+44` or `Sunn O)))` carry other
 * operators. Every Lucene special character is escaped with a backslash so the name is matched as
 * the text it is.
 */
internal object MusicBrainzQuery {

    /** An exact-phrase search on the `artist` field. */
    fun artist(name: String): String = "artist:\"${escape(name)}\""

    /** [term] with each of Lucene's special characters backslash-escaped. */
    fun escape(term: String): String = buildString(term.length) {
        for (c in term) {
            if (c in SPECIAL) append('\\')
            append(c)
        }
    }

    /** `&&` and `||` are operators as pairs; escaping each `&` and `|` covers them. */
    private const val SPECIAL = "+-&|!(){}[]^\"~*?:\\/"
}
