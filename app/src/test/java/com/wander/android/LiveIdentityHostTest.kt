package com.wander.android

import com.wander.android.core.playback.carriesLiveIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A livestream's manifest, media playlist and segments are three separate requests to three
 * different hosts, and YouTube refuses any of them that arrives without the client identity the
 * stream was minted for — which is what "Stream expired" actually was.
 *
 * These pin down which hosts that identity is handed to, because the answer must be "YouTube's,
 * and nothing else": the headers travel to whatever host is asked for, so a rule that is too
 * loose sends them somewhere they were never meant to go.
 */
class LiveIdentityHostTest {

    @Test
    fun segmentsAndManifestsBothQualify() {
        assertTrue(carriesLiveIdentity("manifest.googlevideo.com"))
        assertTrue(carriesLiveIdentity("rr3---sn-4g5e6nez.googlevideo.com"))
        assertTrue(carriesLiveIdentity("www.youtube.com"))
    }

    /** Case is not significant in a host name, and YouTube does not always send it lower-cased. */
    @Test
    fun matchIsCaseInsensitive() {
        assertTrue(carriesLiveIdentity("Manifest.GoogleVideo.com"))
    }

    /** The registrable domain on its own, with no subdomain, is still theirs. */
    @Test
    fun bareDomainQualifies() {
        assertTrue(carriesLiveIdentity("googlevideo.com"))
    }

    /**
     * The point of matching on `.domain` rather than on a bare suffix: a host that merely *ends*
     * with the text would otherwise be handed headers minted for YouTube.
     */
    @Test
    fun lookalikeDomainIsRefused() {
        assertFalse(carriesLiveIdentity("notgooglevideo.com"))
        assertFalse(carriesLiveIdentity("googlevideo.com.evil.test"))
    }

    /** Another backend's host must never receive them — this is the whole reason for the scope. */
    @Test
    fun otherSourcesAreRefused() {
        assertFalse(carriesLiveIdentity("music.example.org"))
        assertFalse(carriesLiveIdentity("archive.org"))
        assertFalse(carriesLiveIdentity(null))
    }
}
