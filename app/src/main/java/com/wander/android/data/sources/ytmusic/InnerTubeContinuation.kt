package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.JsonObject

/**
 * The token that fetches the next page of a long shelf — liked songs, a library grid, a playlist.
 *
 * InnerTube hands out only the first hundred-odd rows of anything; the rest is behind one of
 * these. Two shapes are in circulation and both are still served:
 *
 *  - **legacy** — `continuations[0].nextContinuationData.continuation` on the shelf itself, which
 *    is replayed as the `ctoken`/`continuation` query parameters of the next `browse`.
 *  - **modern** — a trailing `continuationItemRenderer` row whose `continuationCommand.token` is
 *    sent back in the request body's `continuation` field.
 */
internal data class InnerTubeContinuation(val token: String, val legacy: Boolean)

/**
 * The next-page token of the first list-like shelf in a browse response, first page or later.
 *
 * Deliberately not a sweep for every `nextContinuationData` in the document: a playlist page's
 * `sectionListRenderer` carries one too, and following *that* loads "Suggestions" — songs that are
 * not in the playlist at all. Only shelves that hold rows, and the containers continuation pages
 * answer with, are looked at.
 */
internal fun JsonObject.shelfContinuation(): InnerTubeContinuation? =
    SHELF_CONTAINERS.firstNotNullOfOrNull { key ->
        renderers(key).firstNotNullOfOrNull { it.ownContinuation() }
    }

private fun JsonObject.ownContinuation(): InnerTubeContinuation? {
    val legacy = this["continuations"]?.array()
        ?.firstNotNullOfOrNull { it.path("nextContinuationData", "continuation").text() }
        ?.takeIf { it.isNotBlank() }
    if (legacy != null) return InnerTubeContinuation(legacy, legacy = true)

    val modern = ROW_LISTS.firstNotNullOfOrNull { list ->
        this[list]?.array()?.lastOrNull().path(
            "continuationItemRenderer",
            "continuationEndpoint",
            "continuationCommand",
            "token"
        ).text()
    }?.takeIf { it.isNotBlank() }
    return modern?.let { InnerTubeContinuation(it, legacy = false) }
}

/** Shelves that hold rows on a first page, then the wrappers their later pages arrive in. */
private val SHELF_CONTAINERS = listOf(
    "musicPlaylistShelfRenderer",
    "musicShelfRenderer",
    "gridRenderer",
    "musicPlaylistShelfContinuation",
    "musicShelfContinuation",
    "gridContinuation",
    "appendContinuationItemsAction"
)

/** Where a container keeps its rows, and so where a trailing continuation row would be. */
private val ROW_LISTS = listOf("contents", "items", "continuationItems")
