package com.wander.android.ui.components

import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one line the listener reads while following someone.
 *
 * Its failure modes are all "says nothing useful": an unresolvable track, no track yet, and — the
 * one this covers — a session sealed to a key this device does not hold, which used to render the
 * server's placeholder against an empty artist.
 */
class ListenAlongStatusLineTest {

    private fun session(now: AgroFriendNowPlaying?, unresolvable: String? = null) =
        ListenAlongSession(
            host = "alpha",
            listenerCount = 1,
            nowPlaying = now,
            resolvedFrom = null,
            unresolvable = unresolvable
        )

    private fun track(title: String, artist: String, locked: Boolean = false) =
        AgroFriendNowPlaying(
            username = "alpha",
            trackUri = "ytm:abc",
            trackTitle = title,
            artistName = artist,
            albumName = null,
            artworkUrl = null,
            positionMs = 0L,
            isPlaying = true,
            updatedAt = "",
            isLocked = locked
        )

    @Test
    fun anOrdinarySessionNamesTheTrack() {
        assertEquals(
            "Kid A — Radiohead",
            session(track("Kid A", "Radiohead")).statusLine()
        )
    }

    @Test
    fun aSealedSessionThatWillNotOpenSaysSoRatherThanShowingAPlaceholder() {
        // What the server actually sends when it cannot be opened: the placeholder title and an
        // empty artist. Rendered naively that is "Private Session — ", which reads as a bug.
        val line = session(track("Private Session", "", locked = true)).statusLine()

        assertTrue("the placeholder leaked into the line: $line", !line.endsWith("— "))
        assertEquals("Private session — you can't open this one", line)
    }

    @Test
    fun anUnresolvableTrackStillWinsOverEverythingElse() {
        // Ordered deliberately: not being able to find the track is the more actionable of the two.
        assertEquals(
            "Can't find “Kid A” in any of your sources",
            session(track("Kid A", "Radiohead"), unresolvable = "Kid A").statusLine()
        )
    }

    @Test
    fun aSessionWithNothingPlayingWaits() {
        assertEquals("Waiting for them to play something", session(null).statusLine())
    }
}
