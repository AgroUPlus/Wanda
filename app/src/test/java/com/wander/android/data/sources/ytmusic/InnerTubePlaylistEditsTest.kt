package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubePlaylistEditsTest {

    @Test
    fun onlyUserPlaylistsAreEditable() {
        assertEquals("PLabc", editablePlaylistId("ytm:VLPLabc"))
        assertNull(editablePlaylistId("ytm:VLLM"))
        assertNull(editablePlaylistId("ytm:FEmusic_liked_videos"))
        assertNull(editablePlaylistId("ytm:VLOLAK5uy_album"))
        assertEquals("ytm:VLPLabc", browsablePlaylistId("PLabc"))
    }

    /** `ACTION_MOVE_VIDEO_BEFORE` names the entry the moved one should end up in front of. */
    @Test
    fun successorIsWhateverFollowsTheMovedEntryAfterTheMove() {
        val entries = listOf("a", "b", "c", "d")
        assertEquals("d", successorAfterMove(entries, from = 0, to = 2)) // b c a d
        assertEquals("a", successorAfterMove(entries, from = 3, to = 0)) // d a b c
        assertNull(successorAfterMove(entries, from = 1, to = 3)) // a c d b
    }

    @Test
    fun readsEntryIdsInTrackOrderSkippingRowsThatAreNotTracks() {
        fun row(videoId: String, setId: String) = """
            { "musicResponsiveListItemRenderer": {
                "flexColumns": [ { "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [ { "text": "Song $videoId" } ] } } } ],
                "playlistItemData": { "videoId": "$videoId", "playlistSetVideoId": "$setId" } } }
        """
        val page = Json.parseToJsonElement(
            """{ "contents": [ ${row("v1", "s1")},
                 { "musicResponsiveListItemRenderer": { "flexColumns": [] } },
                 ${row("v1", "s2")} ] }"""
        ).jsonObject

        assertEquals(
            listOf(PlaylistEntryRef("v1", "s1"), PlaylistEntryRef("v1", "s2")),
            page.playlistEntryRefs()
        )
    }

    @Test
    fun anEditIsRefusedOnlyWhenTheBodyNamesAnotherStatus() {
        fun body(text: String) = Json.parseToJsonElement(text).jsonObject
        assertTrue(body("""{ "status": "STATUS_SUCCEEDED" }""").editSucceeded())
        assertTrue(body("{}").editSucceeded())
        assertFalse(body("""{ "status": "STATUS_FAILED" }""").editSucceeded())
    }
}
