package com.wander.android.data.sources.navidrome

/**
 * Turns `getArtistInfo2`'s biography into something a `Text` can show.
 *
 * Navidrome hands the field on from whatever metadata agent produced it, and Last.fm's is HTML:
 * a paragraph of prose ending in a "Read more on Last.fm" anchor. Rendered as-is the user reads
 * `<a href="https://www.last.fm/music/...">` in the middle of a sentence.
 *
 * The tags are stripped rather than rendered. This is one paragraph on an artist page, not a
 * document, and the trailing link goes with them — it points at somebody else's website, which
 * nothing on this screen can open.
 *
 * Returns null for a biography that is empty once stripped, because a server that answered with
 * only a link has told us nothing about the artist.
 */
internal fun String.stripBiographyMarkup(): String? = this
    .replace(READ_MORE, "")
    .replace(HTML_TAG, "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()
    .takeIf { it.isNotEmpty() }

private val HTML_TAG = Regex("<[^>]*>")
private val READ_MORE = Regex("<a[^>]*>\\s*Read more on Last\\.fm\\s*</a>[\\s\\S]*$")
