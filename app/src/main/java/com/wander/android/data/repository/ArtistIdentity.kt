package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Tells two artists who share a name apart.
 *
 * Room finds an artist's work with `WHERE artist = :name COLLATE NOCASE`, and that is not a
 * mistake — one artist genuinely reaches Room capitalised differently by different backends, so an
 * exact match would split their discography in half. The cost is that two *different* artists whose
 * names differ only in case become one: a Japanese singer called "misa" and an unrelated "MISA"
 * arrive as the same page, with each other's songs on it.
 *
 * The name cannot settle it, so identity does. Where a backend published an artist id — YouTube
 * Music always does — an item carrying a *different* id is definitely somebody else and is dropped.
 *
 * What is deliberately **not** done is preferring an exact-case name match when no ids are
 * available. It would fix the same-name case for sources that publish no ids, and it would break
 * the case this query was written for, silently hiding the half of a discography that a second
 * backend spelled differently. An item we cannot disprove is kept; a page with a stranger's song on
 * it is a smaller failure than a page missing the user's own music.
 */
internal object ArtistIdentity {

    /**
     * Discovers all backend ids that refer to this artist, by seeing which of their tracks
     * deduplicate against one another across sources.
     */
    fun aliasesOf(
        tracks: List<UnifiedTrack>,
        pageArtistId: String?
    ): Set<String> {
        val groups = TrackDeduplicator.groupRecordings(tracks)
        val aliases = mutableSetOf<String>()
        if (pageArtistId != null) {
            aliases.add(pageArtistId)
            // Every id from the *same backend* as the trusted id is trusted too. `tracks` already
            // came from Room keyed on this exact folded name, so a second id from that backend is
            // not a stranger sharing the name — it is this artist, credited inconsistently across
            // releases (a Navidrome/ID3-tagging reality: two albums can carry two different artist
            // rows for the same person). Below, the loop only bridges ids *across* backends, through
            // a literal same-recording match — nothing rescues a same-backend split, which is why
            // one narrow id locked in by a cache hit or a tapped track used to make every other
            // release by that artist vanish on the very next visit.
            val seedSource = tracks.firstOrNull { it.artistId == pageArtistId }?.source
            if (seedSource != null) {
                tracks.asSequence()
                    .filter { it.source == seedSource }
                    .mapNotNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
                    .forEach(aliases::add)
            }
        } else {
            val firstId = tracks.firstNotNullOfOrNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
            if (firstId != null) aliases.add(firstId)
        }
        if (aliases.isEmpty()) return emptySet()

        var added = true
        while (added) {
            added = false
            for (group in groups) {
                val groupIds = group.mapNotNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
                if (groupIds.any { it in aliases }) {
                    val newIds = groupIds.filterNot { it in aliases }
                    if (newIds.isNotEmpty()) {
                        aliases.addAll(newIds)
                        added = true
                    }
                }
            }
        }
        return aliases
    }

    /**
     * Whether two artist names denote the same person.
     *
     * Deliberately lenient — case, surrounding space, runs of inner space and accents are all
     * folded, so "ROSÉ", "Rosé" and "rose" are one artist. The strictness lives in the *ids*; this
     * exists only to catch a page that is plainly about somebody else, and a false mismatch costs
     * a real portrait while a false match costs nothing this function is trusted to prevent.
     *
     * Accents are folded rather than compared because the same artist reaches us spelled both ways
     * by different backends — a server that strips diacritics on import is common — and splitting
     * them would be the "half a discography" failure `sameArtist` is written to avoid.
     */
    fun sameName(a: String, b: String): Boolean = a.foldedName() == b.foldedName()

    /**
     * Case, accents, a leading article, a trailing "(feat. ...)" credit and punctuation all fold
     * away. Every one of these is a real way the *same* artist reaches Room spelled differently:
     * "The Beatles" vs. Navidrome's "Beatles, The"-style tagging is not covered (word order, not a
     * strippable affix) but a leading article and a featured-artist credit tacked on by one backend
     * and not the other were the two most common causes of the artist page silently coming up empty
     * for a name that was, underneath, a match.
     */
    private fun String.foldedName(): String =
        java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace(FEATURE_CREDIT, "")
            .replace(LEADING_ARTICLE, "")
            .replace(PUNCTUATION, "")
            .replace(WHITESPACE_RUN, " ")
            .trim()

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val LEADING_ARTICLE = Regex("^(the|an?)\\s+")
    // `\b` before the alternation matters: without it, "ft" matched mid-word too, so "Soft Cell"
    // (an "ft" sitting right before a space, same shape as a real "ft " credit) got chopped to "so".
    private val FEATURE_CREDIT = Regex("""[(\[]?\s*\b(feat\.?|featuring|ft\.?)\s+.*$""")
    private val PUNCTUATION = Regex("[.,'’\"!?&]")

    /**
     * Keeps items that could belong to this artist.
     *
     * [aliases] empty means nothing is known about identity and everything is kept.
     * [idOf] returning null likewise means "cannot tell", never "different".
     */
    fun <T> sameArtist(items: List<T>, aliases: Set<String>, idOf: (T) -> String?): List<T> {
        if (aliases.isEmpty()) return items
        return items.filter { item ->
            val id = idOf(item)
            id.isNullOrBlank() || id in aliases
        }
    }

    /**
     * Whether a record's *raw, stored* artist credit — "Artist A, Artist B", "Artist A & Artist B",
     * "Artist A / Artist B" — names [target] among its co-credited artists.
     *
     * The Room queries this backs (`getTracksByArtistFlow`, `getAlbumsByArtistFlow`) had to switch
     * from an exact `artist = :name` match to a substring `LIKE '%name%'` one to catch these rows at
     * all — SQLite has no access to [foldedName]'s folding, so an exact match against "Artist A"
     * could never find a row stored as "Artist A, Artist B" in the first place, whatever [sameName]
     * would have said about it once fetched. That widens what the query returns, which is why every
     * candidate then has to be re-checked here: a `LIKE '%Art%'` also matches "Artisan Collective",
     * and this is what tells the two apart.
     *
     * [sameName] alone already covers a lone "feat."/"ft." tail — [foldedName] strips it — so this
     * only has to add splitting on the separators an equal-billing credit actually uses.
     */
    fun creditsMatch(rawArtist: String, target: String): Boolean {
        if (sameName(rawArtist, target)) return true
        return rawArtist.split(CREDIT_SEPARATORS).any { sameName(it, target) }
    }

    private val CREDIT_SEPARATORS = Regex("""\s*[,&/]\s*|\s+[xX]\s+""")
}
