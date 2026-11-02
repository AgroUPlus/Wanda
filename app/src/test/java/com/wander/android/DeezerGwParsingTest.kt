package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.deezer.DeezerGwParsing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gateway's upper-case, stringly-typed rows, read into the ids the public API's tracks carry. */
class DeezerGwParsingTest {

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `a favourite song gets the same id and cover a search result would`() {
        val track = DeezerGwParsing.track(
            obj(
                """{"SNG_ID":"3135556","SNG_TITLE":"Harder, Better, Faster, Stronger","ART_NAME":"Daft Punk",
                "ART_ID":"27","ALB_ID":"302127","ALB_TITLE":"Discovery","ALB_PICTURE":"2e018122cb56986277102d2041a592c8",
                "DURATION":"224","TRACK_NUMBER":"4","DISK_NUMBER":"1"}"""
            )
        )!!
        assertEquals("deezer:3135556", track.id)
        assertEquals(SourceType.DEEZER, track.source)
        assertEquals("deezer:302127", track.albumId)
        assertEquals(224_000L, track.durationMs)
        assertEquals(4, track.trackNumber)
        assertTrue(track.artworkUrl!!.contains("/cover/2e018122cb56986277102d2041a592c8/"))
    }

    @Test
    fun `a version is kept in the title`() {
        val track = DeezerGwParsing.track(obj("""{"SNG_ID":"1","SNG_TITLE":"Song","VERSION":"(Live)"}"""))!!
        assertEquals("Song (Live)", track.title)
    }

    @Test
    fun `a row without an id is skipped`() {
        assertNull(DeezerGwParsing.track(obj("""{"SNG_TITLE":"Song"}""")))
    }

    @Test
    fun `only the account's own playlists are editable`() {
        val own = obj("""{"PLAYLIST_ID":"9","TITLE":"Mine","PARENT_USER_ID":"42","NB_SONG":"12"}""")
        val followed = obj("""{"PLAYLIST_ID":"10","TITLE":"Theirs","PARENT_USER_ID":"7"}""")
        assertTrue(DeezerGwParsing.playlist(own, "42")!!.isEditable)
        assertEquals(12, DeezerGwParsing.playlist(own, "42")!!.songCount)
        assertFalse(DeezerGwParsing.playlist(followed, "42")!!.isEditable)
    }
}
