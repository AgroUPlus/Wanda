package com.wander.android.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A service signing the device out is remembered apart from the user signing out. */
class ScrobbleSignOutTest {

    @Test
    fun aServiceSignOutKeepsItsReasonAcrossRestarts() {
        val prefs = FakeSharedPreferences()
        SecureScrobblePreferences(prefs).apply {
            connectLastFm("me", "session")
            disconnectLastFm("Invalid session key")
        }
        val reopened = SecureScrobblePreferences(prefs)
        assertEquals("Invalid session key", reopened.lastFmSignedOut)
        assertNull(reopened.lastFmSession)
    }

    @Test
    fun connectingAgainClearsTheReason() {
        val scrobbling = SecureScrobblePreferences(FakeSharedPreferences())
        scrobbling.disconnectListenBrainz("revoked")
        scrobbling.connectListenBrainz("me", "token")
        assertNull(scrobbling.listenBrainzSignedOut)
    }

    @Test
    fun theUsersOwnSignOutLeavesNoReason() {
        val scrobbling = SecureScrobblePreferences(FakeSharedPreferences())
        scrobbling.disconnectLastFm("Invalid session key")
        scrobbling.disconnectLastFm()
        assertNull(scrobbling.lastFmSignedOut)
    }
}
