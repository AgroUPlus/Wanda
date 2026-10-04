package com.wander.android.data.sources.agro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A recap as Agro sends it, and as Room keeps it — the same `@Serializable` shape for both, so these
 * are the two places it could drift.
 */
class JamRecapJsonTest {

    private val fromServer = """
        {
          "startedAt": "2026-10-04T20:00:00.123456789+00:00",
          "endedAt": "2026-10-04T21:30:00+00:00",
          "durationMs": 5400000,
          "people": ["alex", "sam"],
          "tracksOmitted": 0,
          "tracks": [
            {"title": "One", "artist": "A", "artworkUrl": null, "trackUri": "u:1",
             "addedBy": "sam", "durationMs": 1000, "approvals": 2, "skipVotes": 0}
          ],
          "topContributor": {"username": "sam", "tracks": 1},
          "mostLoved": {"title": "One", "artist": "A", "addedBy": "sam"},
          "mostSkipped": null,
          "aFieldFromANewerServer": true
        }
    """.trimIndent()

    @Test
    fun aRecapFromTheServerDecodesIgnoringFieldsItDoesNotKnow() {
        val recap = JamRecapJson.decodeFromString(JamRecap.serializer(), fromServer)
        assertEquals(listOf("alex", "sam"), recap.people)
        assertEquals("One", recap.tracks.single().title)
        assertEquals(2L, recap.tracks.single().approvals)
        assertEquals("sam", recap.topContributor?.username)
        assertEquals(0L, recap.mostLoved?.skipVotes)
        assertNull(recap.mostSkipped)
    }

    @Test
    fun whatRoomStoresReadsBackUnchanged() {
        val recap = JamRecapJson.decodeFromString(JamRecap.serializer(), fromServer)
        val stored = JamRecapJson.encodeToString(JamRecap.serializer(), recap)
        assertEquals(recap, JamRecapJson.decodeFromString(JamRecap.serializer(), stored))
    }
}
