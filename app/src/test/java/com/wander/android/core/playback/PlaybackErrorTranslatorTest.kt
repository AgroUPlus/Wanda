package com.wander.android.core.playback

import com.wander.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorTranslatorTest {
    private fun say(raw: String?) = PlaybackErrorTranslator.fromMessage(raw, "ERROR_CODE_TEST")
    private fun res(id: Int) = PlaybackMessage.Resource(id)

    @Test
    fun anExpiredYouTubeSessionAsksForASignIn() {
        val expected = res(R.string.playback_error_youtube_session)

        assertEquals(expected, say("YouTube Music refused the request (401)"))
        assertEquals(expected, say("YouTube Music refused the request (403)"))
    }

    @Test
    fun aRefusalThatIsNotAnAuthErrorIsNotTreatedAsOne() {
        assertEquals(PlaybackMessage.Verbatim("YouTube Music refused (500)"), say("YouTube Music refused (500)"))
    }

    @Test
    fun aTransformFailureIsTransientAndSaysToRetry() {
        val expected = res(R.string.playback_error_youtube_prepare)

        assertEquals(expected, say("Could not unscramble the signature"))
        assertEquals(expected, say("bad throttling parameter"))
    }

    @Test
    fun anUnplayableTrackIsNamedAsSuch() {
        assertEquals(res(R.string.playback_error_youtube_unplayable), say("There is no playable audio"))
        assertEquals(res(R.string.playback_error_youtube_refused), say("YouTube will not play this track"))
    }

    @Test
    fun anExpiredStreamUrlSaysToPlayItAgain() {
        assertEquals(res(R.string.playback_error_stream_expired), say("Response code: 403"))
        assertEquals(res(R.string.playback_error_stream_expired), say("Response code: 410"))
    }

    @Test
    fun anUnreachableHostSaysToCheckTheConnection() {
        val expected = res(R.string.playback_error_unreachable)

        assertEquals(expected, say("Unable to connect to host"))
        assertEquals(expected, say("UnknownHostException: music.example"))
    }

    @Test
    fun anUnrecognisedMessageIsShownAsItIs() {
        assertEquals(PlaybackMessage.Verbatim("something odd"), say("something odd"))
    }

    @Test
    fun noMessageAtAllFallsBackToTheErrorCodeName() {
        assertEquals(
            PlaybackMessage.Resource(R.string.playback_error_generic, listOf("ERROR_CODE_TEST")),
            say(null)
        )
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
