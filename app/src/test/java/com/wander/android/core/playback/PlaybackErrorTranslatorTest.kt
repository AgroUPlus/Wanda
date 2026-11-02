package com.wander.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorTranslatorTest {
    private fun say(raw: String?) = PlaybackErrorTranslator.fromMessage(raw, "ERROR_CODE_TEST")

    @Test
    fun anExpiredYouTubeSessionAsksForASignIn() {
        val expected = "Sign in to YouTube Music again — the session expired."

        assertEquals(expected, say("YouTube Music refused the request (401)"))
        assertEquals(expected, say("YouTube Music refused the request (403)"))
    }

    @Test
    fun aRefusalThatIsNotAnAuthErrorIsNotTreatedAsOne() {
        assertEquals("YouTube Music refused (500)", say("YouTube Music refused (500)"))
    }

    @Test
    fun aTransformFailureIsTransientAndSaysToRetry() {
        val expected = "Couldn't prepare the YouTube stream. Try again in a moment."

        assertEquals(expected, say("Could not unscramble the signature"))
        assertEquals(expected, say("bad throttling parameter"))
    }

    @Test
    fun anUnplayableTrackIsNamedAsSuch() {
        assertEquals("This track isn't playable from YouTube Music.", say("There is no playable audio"))
        assertEquals("YouTube Music won't play this track here.", say("YouTube will not play this track"))
    }

    @Test
    fun anExpiredStreamUrlSaysToPlayItAgain() {
        assertEquals("Stream expired. Play it again to refresh it.", say("Response code: 403"))
        assertEquals("Stream expired. Play it again to refresh it.", say("Response code: 410"))
    }

    @Test
    fun anUnreachableHostSaysToCheckTheConnection() {
        val expected = "Can't reach the source. Check your connection."

        assertEquals(expected, say("Unable to connect to host"))
        assertEquals(expected, say("UnknownHostException: music.example"))
    }

    @Test
    fun anUnrecognisedMessageIsShownAsItIs() {
        assertEquals("something odd", say("something odd"))
    }

    @Test
    fun noMessageAtAllFallsBackToTheErrorCodeName() {
        assertEquals("Playback failed (ERROR_CODE_TEST).", say(null))
    }

    @Test
    fun onlyA403Or410FromTheStreamHostCountsAsAnExpiredStream() {
        assertTrue(PlaybackErrorTranslator.isExpiredStreamMessage("Response code: 403"))
        assertTrue(PlaybackErrorTranslator.isExpiredStreamMessage("Response code: 410"))
        assertFalse(PlaybackErrorTranslator.isExpiredStreamMessage("Response code: 404"))
        // A refused session is a sign-in problem, which fetching the stream again will not fix.
        assertFalse(PlaybackErrorTranslator.isExpiredStreamMessage("YouTube Music refused the request (403)"))
    }

    @Test
    fun anUnreachableHostIsAConnectivityFailure() {
        assertTrue(PlaybackErrorTranslator.isConnectivityMessage("Unable to connect to host"))
        assertTrue(PlaybackErrorTranslator.isConnectivityMessage("java.net.UnknownHostException: music.example"))
        assertFalse(PlaybackErrorTranslator.isConnectivityMessage("There is no playable audio"))
    }
}
