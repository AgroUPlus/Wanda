package com.wander.android

import com.wander.android.data.repository.AgroPlaylistLink
import com.wander.android.data.sources.agro.AgroPlaylistApi
import com.wander.android.data.sources.agro.AgroPlaylistTrack
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgroPlaylistTest {
    private val id = "3f2b8c1e-9a4d-4e57-8b21-6d0f5c7a9e10"

    @Test
    fun anAgroLinkCarriesOnlyTheId() {
        val uri = AgroPlaylistLink.toUri(id)

        assertEquals("wanda://playlist?agro=$id", uri)
        assertEquals(id, AgroPlaylistLink.parse(uri))
        assertTrue(AgroPlaylistLink.isAgroLink(uri))
    }

    @Test
    fun refusesAnIdThatIsNotAUuid() {
        assertNull(AgroPlaylistLink.parse("wanda://playlist?agro=1%20OR%201=1"))
        assertNull(AgroPlaylistLink.parse("wanda://playlist?agro="))
        assertTrue(AgroPlaylistLink.isAgroLink("wanda://playlist?agro=nope"))
    }

    @Test
    fun aTracksLinkIsNotAnAgroLink() {
        assertFalse(AgroPlaylistLink.isAgroLink("wanda://playlist?name=Mix&d=abc"))
        assertNull(AgroPlaylistLink.parse("wanda://album?agro=$id"))
    }

    @Test
    fun aBatchIsOneRequestOfOrderedAliasedMutationsWithinTheServersComplexityLimit() {
        val count = AgroPlaylistApi.BATCH
        val query = AgroPlaylistApi.addTracksQuery(count)

        assertEquals(count, Regex("""\ba\d+: addTrackToPlaylist""").findAll(query).count())
        assertTrue(query.indexOf("a0:") < query.indexOf("a1:"))
        assertTrue(query.contains("\$t${count - 1}: PlaylistTrackInput!"))
        // Two units a mutation (the field and its `id`); the server refuses over 500.
        assertTrue(count * 2 <= 500 / 4)
    }

    @Test
    fun batchVariablesCarryEachTrackAndDropEmptyOptionals() {
        val vars = AgroPlaylistApi.addTracksVariables(
            id,
            listOf(AgroPlaylistTrack("One", "A", "Album", 1000L), AgroPlaylistTrack("Two", "B", null, 0L))
        )

        assertEquals(id, vars["id"]!!.jsonPrimitive.content)
        val second = vars["t1"]!!.jsonObject
        assertEquals("Two", second["title"]!!.jsonPrimitive.content)
        assertFalse("album" in second)
        assertFalse("durationMs" in second)
        assertEquals("1000", vars["t0"]!!.jsonObject["durationMs"]!!.jsonPrimitive.content)
    }
}
