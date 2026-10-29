package com.wander.android.data.repository

/**
 * The separate artists inside one stored credit string.
 *
 * Every backend hands Room a single `artist` column, and a track by several people arrives there as
 * "A, B & C", "A; B" (ID3 multi-value tags, as MediaStore joins them), "A feat. B", "A x B" or
 * "A、B". Read whole, that string is one artist nobody is called: tapping "Artist" opened a page for
 * "A, B & C", and B's own page never listed the song.
 *
 * Separators that are also common *inside* band names — "&" (Simon & Garfunkel), " x " — are still
 * split on, which is why [isAmbiguous] exists: a caller offering the names to pick from keeps the
 * whole credit as an option when splitting may have broken a real name apart.
 */
object ArtistCredits {

    /** The names in [raw], in billing order, without duplicates. A single artist comes back alone. */
    fun split(raw: String): List<String> {
        val whole = raw.trim()
        if (whole.isEmpty()) return emptyList()
        val names = whole
            .replace(BRACKETED_FEATURE, " feat. $1")
            .split(SEPARATORS)
            .map { it.trim().trim('(', ')', '[', ']').trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        return names.ifEmpty { listOf(whole) }
    }

    /**
     * Whether [split] may have cut a single name in two.
     *
     * True when the credit leans on "&", "+", " x " or ", The" — joins that are as often part of one
     * act's name ("Earth, Wind & Fire", "Tyler, The Creator") as a list of several.
     */
    fun isAmbiguous(raw: String): Boolean = split(raw).size > 1 && AMBIGUOUS.containsMatchIn(raw)

    /**
     * The names to offer someone choosing whose page to open: each artist, with the whole credit
     * first when [isAmbiguous] says it may be one act's name. A single artist comes back alone.
     */
    fun choices(raw: String): List<String> {
        val names = split(raw)
        return if (isAmbiguous(raw)) listOf(raw.trim()) + names else names
    }

    /** "(feat. B)" and "[ft. B]" become a plain " feat. B" so the split below sees one shape. */
    private val BRACKETED_FEATURE =
        Regex("""[(\[]\s*(?:feat\.?|featuring|ft\.?)\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)

    // "/" only with space around it: unspaced, it is as likely to be "AC/DC" as a list. "and" and
    // "with" are left alone entirely — "Florence and the Machine" is one act far more often than two.
    // Feature words need a word boundary so "Soft Cell" keeps its "ft".
    private val SEPARATORS = Regex(
        """\s*[,;、，]\s*|\s+/\s+|\s*&\s*|\s+\+\s+|\s+[xX×]\s+|\s+vs\.?\s+|\s+\b(?:feat\.?|featuring|ft\.?)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val AMBIGUOUS = Regex("""&|\s\+\s|\s[xX×]\s|,\s*the\s""", RegexOption.IGNORE_CASE)
}

/**
 * The backend id to open [name]'s page with: the track's own id when [name] is its whole credit,
 * else none. The id names whoever the backend filed the track under, and handing it to a page for
 * one name picked out of "A, B" would show that page as somebody else.
 */
fun com.wander.android.data.model.UnifiedTrack.artistIdFor(name: String): String? =
    artistId.takeIf { name.trim().equals(artist.trim(), ignoreCase = true) }
