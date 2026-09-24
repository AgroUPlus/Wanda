package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Navidrome/Subsonic and YouTube Music account credentials stored in encrypted storage.
 */
internal class SecureAccountPreferences(private val prefs: SharedPreferences) {

    private val _navidromeConfigured = MutableStateFlow(hasNavidromeCredentials())
    val navidromeConfigured: StateFlow<Boolean> = _navidromeConfigured.asStateFlow()

    val navidromeServerUrl: String get() = prefs.getString(KEY_NAVIDROME_URL, "").orEmpty()
    val navidromeUsername: String get() = prefs.getString(KEY_NAVIDROME_USER, "").orEmpty()
    val navidromePassword: String get() = prefs.getString(KEY_NAVIDROME_TOKEN, "").orEmpty()

    fun setNavidromeCredentials(url: String, username: String, password: String) {
        prefs.edit {
            putString(KEY_NAVIDROME_URL, url.trim().trimEnd('/'))
            putString(KEY_NAVIDROME_USER, username.trim())
            putString(KEY_NAVIDROME_TOKEN, password)
        }
        _navidromeConfigured.value = hasNavidromeCredentials()
    }

    fun clearNavidromeCredentials() {
        prefs.edit {
            remove(KEY_NAVIDROME_URL)
            remove(KEY_NAVIDROME_USER)
            remove(KEY_NAVIDROME_TOKEN)
        }
        _navidromeConfigured.value = false
    }

    private fun hasNavidromeCredentials() =
        navidromeServerUrl.isNotBlank() && navidromeUsername.isNotBlank() && navidromePassword.isNotBlank()

    val ytMusicAuthCookie: String get() = prefs.getString(KEY_YTM_COOKIE, "").orEmpty()
    val ytMusicVisitorData: String get() = prefs.getString(KEY_YTM_VISITOR, "").orEmpty()

    private val _ytMusicConfigured = MutableStateFlow(ytMusicAuthCookie.isNotBlank())
    val ytMusicConfigured: StateFlow<Boolean> = _ytMusicConfigured.asStateFlow()

    var ytMusicAccountName: String
        get() = prefs.getString(KEY_YTM_ACCOUNT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_YTM_ACCOUNT, value.trim()) }

    fun setYtMusicSession(cookie: String, visitorData: String = ytMusicVisitorData) {
        prefs.edit {
            putString(KEY_YTM_COOKIE, cookie.trim())
            putString(KEY_YTM_VISITOR, visitorData)
        }
        _ytMusicConfigured.value = cookie.isNotBlank()
    }

    fun clearYtMusicSession() {
        prefs.edit {
            remove(KEY_YTM_COOKIE)
            remove(KEY_YTM_VISITOR)
            remove(KEY_YTM_ACCOUNT)
        }
        _ytMusicConfigured.value = false
    }

    fun resetFlows() {
        _navidromeConfigured.value = false
        _ytMusicConfigured.value = false
    }
}
