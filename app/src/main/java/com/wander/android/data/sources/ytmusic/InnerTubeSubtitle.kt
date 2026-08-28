package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * The `Song • Artist • Album • 3:45` line under a YouTube Music row, taken apart.
 *
 * Reading it by position does not work. Search rows open with a *type* label — "Song", "Video",
 * "Single" — while library and radio rows start straight at the artist, so run 0 was the artist
 * on some screens and the literal word "Song" on others. That is where tracks credited to an
 * artist called "Song" came from.
 *
 * Nor can the label simply be skipped by name: `hl` is the device language, so it arrives
 * translated. What is reliable is the *shape* — a type label is plain text, whereas an artist and
 * an album are links to their own pages — so the parts are identified by what they point at.
 */
internal class InnerTubeSubtitle private constructor(private val tokens: List<JsonElement>) {

    private val artistRun: JsonElement? = tokens.firstOrNull { it.pageType() == ARTIST_PAGE }

    val artist: String? = artistRun?.textOf() ?: fallbackArtist()

    /**
     * The artist only when a run actually links to their page.
     *
     * The fallback above is right for a *track* row, which always names its artist somewhere in
     * the line. It is wrong for a record tile on an artist's own page, where the subtitle is
     * `Album • 2023` — no links at all, so the fallback took the first token and credited the
     * album to an artist called "Album", or to "2023" on the tiles that print only a year. The
     * caller there already knows whose page it is; this lets it tell a real credit from a guess.
     */
    val linkedArtist: String? = artistRun?.textOf()

    /** The release year, when the line prints one. Distinguished by shape, like everything here. */
    val year: Int? = tokens.firstNotNullOfOrNull { token ->
        token.textOf()?.trim()?.takeIf(YEAR::matches)?.toIntOrNull()
    }

    /**
     * The artist's own page, when the run linking to it says which one.
     *
     * This was already being located in order to identify the artist token and then thrown away
     * with the rest of the endpoint — so YouTube Music tracks carried an artist *name* and no id,
     * and every feature keyed on an artist id (sharing one, opening their real page) was silently
     * unavailable for the one source that always knows it. Null on uploads YouTube has not matched
     * to an artist, which is a genuine absence rather than a parsing failure.
     */
    val artistId: String? = artistRun.path("navigationEndpoint", "browseEndpoint", "browseId").text()

    /**
     * The album link when there is one, else the token after the artist — which is where an album
     * sits on rows YouTube did not link.
     *
     * The fallback is held to the same shape test the artist fallback uses. It used to reject only
     * durations, so a row reading `Artist • 15M views` filed the song under an album called
     * "15M views" — two hundred and forty-two of them in one library, which then read as two
     * different records when the same song turned up twice.
     */
    val album: String? = tokens.firstOrNull { it.pageType() == ALBUM_PAGE }?.textOf()
        ?: withoutTypeLabel().drop(1).firstOrNull()?.textOf()?.trim()?.takeIf(::couldTitle)

    /** `mm:ss` or `h:mm:ss`, always last when present — and absent on rows that have no length. */
    val duration: String? = tokens.lastOrNull()?.textOf()?.takeIf(DURATION::matches)

    /**
     * A best guess at the artist when no run links to one.
     *
     * Needed for uploads YouTube has not matched to an artist, where the name is plain text and
     * nothing points anywhere. The danger is a *release* line — `Single • 2023`, `Album • 2019` —
     * which names a kind and a year and no person at all, and whose first token the old fallback
     * happily returned: albums by an artist called "Single", and before that, by one called "2023".
     *
     * A release line is told apart by its year, which is shape rather than vocabulary — the labels
     * themselves arrive translated and cannot be matched by name. So when the line prints a year
     * and links to nobody, the leading token is a kind and is dropped; and no token that is itself
     * a year, a duration or a count is ever offered as a name. If nothing survives that, the line
     * genuinely does not say who this is, and null is the honest answer — callers with better
     * information fill it in, and `parseResponsiveListItem` is explicit that it does not know.
     */
    private fun fallbackArtist(): String? {
        val candidates = withoutTypeLabel()
        val looksLikeRelease = candidates.any { it.textOf()?.trim()?.let(YEAR::matches) == true } &&
            candidates.none { it.navigationEndpoint() != null }
        return (if (looksLikeRelease) candidates.drop(1) else candidates)
            .mapNotNull { it.textOf()?.trim() }
            .firstOrNull(::couldName)
    }

    /**
     * Whether a token could be a record's title.
     *
     * Looser than [couldName] in one specific way: a year is a perfectly good album title —
     * *1989*, *2001* — so it is not excluded here even though it can never be an artist. What is
     * excluded is the same in both: lengths, counts, and tokens carrying no letters or digits at
     * all, which is how a stray separator became an album called " & ".
     */
    private fun couldTitle(text: String): Boolean =
        text.any(Char::isLetterOrDigit) &&
            !DURATION.matches(text) &&
            !COUNT.matches(text)

    /** Whether a token could be somebody's name, judged only on what it is shaped like. */
    private fun couldName(text: String): Boolean =
        text.any(Char::isLetterOrDigit) &&
            !YEAR.matches(text) &&
            !DURATION.matches(text) &&
            !COUNT.matches(text)

    /**
     * Used when no run links to an artist page, which happens on uploads YouTube has not matched
     * to one. A type label is plain text sitting in front of runs that *are* links, so that shape
     * — and only that shape — is taken as a label and dropped.
     */
    private fun withoutTypeLabel(): List<JsonElement> {
        val leadsWithPlainText = tokens.firstOrNull()?.navigationEndpoint() == null
        val hasLinks = tokens.any { it.navigationEndpoint() != null }
        return if (leadsWithPlainText && hasLinks) tokens.drop(1) else tokens
    }

    companion object {
        private const val ARTIST_PAGE = "MUSIC_PAGE_TYPE_ARTIST"
        private const val ALBUM_PAGE = "MUSIC_PAGE_TYPE_ALBUM"
        private val DURATION = Regex("""\d+(:\d{2})+""")
        private val YEAR = Regex("""(19|20)\d{2}""")

        /**
         * "1.2M plays", "138K views" — a quantity, never a person.
         *
         * The magnitude suffix or a decimal point is required, and that is the whole point of the
         * rule: without it this also matched "50 Cent", "21 Savage" and "3 Doors Down", and
         * rejecting a real artist as a statistic is a worse failure than letting an unsuffixed
         * "3 views" through. YouTube prints the suffixed form for anything with an audience.
         */
        private val COUNT = Regex("""[\d,]*[.\d][\d,]*[KMB]\s+\S+|[\d,]+\.[\d]+\s+\S+""")

        /** Separator runs carry no meaning and would otherwise count as tokens. */
        private val SEPARATOR = Regex("""[\s•·|]*""")

        fun of(runs: JsonArray?): InnerTubeSubtitle = InnerTubeSubtitle(
            runs.orEmpty().filterNot { run -> SEPARATOR.matches(run.textOf().orEmpty()) }
        )
    }
}

private fun JsonElement?.textOf(): String? = path("text").text()

private fun JsonElement?.navigationEndpoint(): JsonElement? = path("navigationEndpoint")

private fun JsonElement?.pageType(): String? = path(
    "navigationEndpoint",
    "browseEndpoint",
    "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig",
    "pageType"
).text()
