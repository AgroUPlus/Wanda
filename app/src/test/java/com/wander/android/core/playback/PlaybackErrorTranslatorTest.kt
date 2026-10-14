package com.wander.android.core.playback

import org.junit.Assert.assertEquals
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
}
