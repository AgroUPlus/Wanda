package com.wander.android.data.sources.agro

import com.wander.android.data.repository.AgroLiveMessageParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgroSharedPlaylistTest {

    @Test
    fun editAccessNeverExceedsVisibility() {
        assertEquals(EditAccess.OFF, EditAccess.PUBLIC.clampedTo(PlaylistVisibility.PRIVATE))
        assertEquals(EditAccess.FRIENDS, EditAccess.PUBLIC.clampedTo(PlaylistVisibility.FRIENDS))
        assertEquals(EditAccess.PUBLIC, EditAccess.PUBLIC.clampedTo(PlaylistVisibility.PUBLIC))
        assertFalse(EditAccess.FRIENDS.allowedBy(PlaylistVisibility.PRIVATE))
        assertTrue(EditAccess.OFF.allowedBy(PlaylistVisibility.PRIVATE))
    }

    @Test
    fun rolesSayWhatTheyMayDo() {
        assertTrue(PlaylistRole.CONTRIBUTOR.canAdd)
        assertFalse(PlaylistRole.CONTRIBUTOR.canRearrange)
        assertTrue(PlaylistRole.EDITOR.canRearrange)
        assertFalse(PlaylistRole.VIEWER.canAdd)
    }

    @Test
    fun editsAreSentAsTheServersOneOfInput() {
        val json = AgroSharedPlaylistApi.editsJson(
            listOf(
                AgroPlaylistEdit.Add(AgroPlaylistTrack("T", "A", album = " ", durationMs = 0)),
                AgroPlaylistEdit.Remove("r"),
                AgroPlaylistEdit.Move("m", null)
            )
        )
        val add = json[0].jsonObject["add"]!!.jsonObject["track"]!!.jsonObject
        assertEquals("T", add["title"]!!.jsonPrimitive.content)
        assertNull("a blank album is left out", add["album"])
        assertNull("an unknown duration is left out", add["durationMs"])
        assertEquals("r", json[1].jsonObject["remove"]!!.jsonPrimitive.content)
        val move = json[2].jsonObject["move"]!!.jsonObject
        assertEquals("m", move["itemId"]!!.jsonPrimitive.content)
        assertNull("first means no anchor at all", move["afterItemId"])
    }

    @Test
    fun aPlaylistFromANewerServerReadsClosed() {
        val playlist = AgroSharedPlaylistParsing.playlist(
            Json.parseToJsonElement(
                """{"id":"p","userId":"alpha","title":"Mix","visibility":"SOMEDAY","editAccess":"EVERYONE",
                   "myRole":"ADMIN","revision":7,"isFollowing":true,"items":[]}"""
            ).jsonObject
        )
        assertEquals(PlaylistVisibility.PRIVATE, playlist.visibility)
        assertEquals(EditAccess.OFF, playlist.editAccess)
        assertEquals(PlaylistRole.VIEWER, playlist.myRole)
        assertEquals(7L, playlist.revision)
    }

    @Test
    fun aPlaylistPushCarriesItsIdAndRevision() {
        fun payload(text: String) = Json.parseToJsonElement(text).jsonObject

        assertEquals(
            AgroLiveMessage.PlaylistUpdated("p", 4),
            AgroLiveMessageParser.playlistUpdated(payload("""{"id":"p","revision":4}"""))
        )
        assertEquals(
            AgroLiveMessage.PlaylistUpdated("p", null),
            AgroLiveMessageParser.playlistUpdated(payload("""{"id":"p","revision":null}"""))
        )
        assertNull(AgroLiveMessageParser.playlistUpdated(payload("""{"revision":4}""")))
    }

    @Test
    fun aFollowMadeElsewhereNamesThePlaylistAndTheDirection() {
        fun payload(text: String) = Json.parseToJsonElement(text).jsonObject

        assertEquals(
            AgroLiveMessage.PlaylistFollow("p", true),
            AgroLiveMessageParser.playlistFollow(payload("""{"id":"p","following":true}"""))
        )
        assertEquals(
            AgroLiveMessage.PlaylistFollow("p", false),
            AgroLiveMessageParser.playlistFollow(payload("""{"id":"p","following":false}"""))
        )
        // Without a direction there is nothing safe to do: neither keep nor forget a copy on a guess.
        assertNull(AgroLiveMessageParser.playlistFollow(payload("""{"id":"p"}""")))
    }
}
