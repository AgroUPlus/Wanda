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

    val artist: String? = tokens.firstOrNull { it.pageType() == ARTIST_PAGE }?.textOf()
        ?: withoutTypeLabel().firstOrNull()?.textOf()

    /**
     * The album link when there is one, else the token after the artist — which is where an album
     * sits on rows YouTube did not link, and is the duration on rows that have no album at all.
     */
    val album: String? = tokens.firstOrNull { it.pageType() == ALBUM_PAGE }?.textOf()
        ?: withoutTypeLabel().drop(1).firstOrNull()?.textOf()?.takeUnless(DURATION::matches)

    /** `mm:ss` or `h:mm:ss`, always last when present — and absent on rows that have no length. */
    val duration: String? = tokens.lastOrNull()?.textOf()?.takeIf(DURATION::matches)

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
