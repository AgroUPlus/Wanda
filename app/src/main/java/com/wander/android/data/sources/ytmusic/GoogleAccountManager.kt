package com.wander.android.data.sources.ytmusic

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the YouTube Music session. The cookie is the user's own, captured from an in-app sign-in
 * and kept in [SecureStorage]; it is never logged and never leaves the device except in requests
 * to music.youtube.com.
 */
@Singleton
class GoogleAccountManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    val isLoggedIn: StateFlow<Boolean> = secureStorage.ytMusicConfigured

    val authCookie: String get() = secureStorage.ytMusicAuthCookie
    val visitorData: String get() = secureStorage.ytMusicVisitorData

    /** A usable session needs the SAPISID pair that signs each request. */
    fun signIn(cookie: String, visitorData: String = ""): Boolean {
        if (!isUsable(cookie)) return false
        secureStorage.setYtMusicSession(cookie, visitorData)
        return true
    }

    fun signOut() = secureStorage.clearYtMusicSession()

    companion object {
        fun isUsable(cookie: String): Boolean =
            cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID=")
    }
}
