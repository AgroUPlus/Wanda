package com.wander.android.data.sources.ytmusic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Liked songs, library albums and playlists used to stop at InnerTube's first page, about a
 * hundred rows, because no continuation token was ever followed.
 */
class InnerTubeContinuationTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun readsTheLegacyTokenOnAShelf() {
        val page = json(
            """{ "contents": { "musicShelfRenderer": { "contents": [],
                 "continuations": [ { "nextContinuationData": { "continuation": "abc" } } ] } } }"""
        )
        assertEquals(InnerTubeContinuation("abc", legacy = true), page.shelfContinuation())
    }

    @Test
    fun readsTheModernTrailingContinuationRow() {
        val page = json(
            """{ "onResponseReceivedActions": [ { "appendContinuationItemsAction": {
                 "continuationItems": [ { "musicResponsiveListItemRenderer": {} },
                   { "continuationItemRenderer": { "continuationEndpoint": {
                     "continuationCommand": { "token": "next" } } } } ] } } ] }"""
        )
        assertEquals(InnerTubeContinuation("next", legacy = false), page.shelfContinuation())
    }

    /** Following the section list's token would load "Suggestions", not more of the playlist. */
    @Test
    fun ignoresTheSectionListTokenThatLoadsSuggestions() {
        val page = json(
            """{ "sectionListRenderer": {
                 "contents": [ { "musicPlaylistShelfRenderer": { "contents": [] } } ],
                 "continuations": [ { "nextContinuationData": { "continuation": "suggest" } } ] } }"""
        )
        assertNull(page.shelfContinuation())
    }

    @Test
    fun cursorFollowsTokensUntilTheShelfEnds() = runTest {
        val pages = mapOf(
            "b" to page(listOf("3", "4"), next = "c"),
            "c" to page(listOf("5"), next = null)
        )
        var nextCalls = 0
        val cursor = InnerTubeShelfCursor(
            firstPage = { Result.success(page(listOf("1", "2"), next = "b")) },
            nextPage = { nextCalls++; Result.success(pages.getValue(it.token)) },
            parse = { p -> p["rows"]!!.array()!!.mapNotNull { it.text() } }
        )

        assertEquals(listOf("1", "2", "3"), cursor.slice(0, 3).getOrThrow())
        assertEquals(1, nextCalls)
        // Continues from where it stopped rather than re-reading the first pages.
        assertEquals(listOf("4", "5"), cursor.slice(3, 10).getOrThrow())
        assertEquals(2, nextCalls)
        assertEquals(listOf("1", "2", "3", "4", "5"), cursor.all().getOrThrow())
    }

    @Test
    fun aFailedPageFailsTheCallInsteadOfEndingTheShelf() = runTest {
        val cursor = InnerTubeShelfCursor(
            firstPage = { Result.success(page(listOf("1"), next = "b")) },
            nextPage = { Result.failure<JsonObject>(java.io.IOException("offline")) },
            parse = { p -> p["rows"]!!.array()!!.mapNotNull { it.text() } }
        )
        assertTrue(cursor.all().isFailure)
    }

    private fun page(rows: List<String>, next: String?): JsonObject {
        val token = next?.let {
            """, "continuations": [ { "nextContinuationData": { "continuation": "$it" } } ]"""
        }.orEmpty()
        val quoted = rows.joinToString(",") { "\"$it\"" }
        return json("""{ "rows": [ $quoted ], "x": { "musicShelfRenderer": { "contents": [] $token } } }""")
    }
}
