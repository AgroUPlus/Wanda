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

    val deezerAuthArl: String get() = prefs.getString(KEY_DEEZER_ARL, "").orEmpty()

    private val _deezerConfigured = MutableStateFlow(deezerAuthArl.isNotBlank())
    val deezerConfigured: StateFlow<Boolean> = _deezerConfigured.asStateFlow()

    var deezerAccountName: String
        get() = prefs.getString(KEY_DEEZER_ACCOUNT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DEEZER_ACCOUNT, value.trim()) }

    var deezerAccountTier: String
        get() = prefs.getString(KEY_DEEZER_TIER, "FREE").orEmpty()
        set(value) = prefs.edit { putString(KEY_DEEZER_TIER, value.trim()) }

    var deezerAudioQuality: String
        get() = prefs.getString(KEY_DEEZER_QUALITY, "AUTO").orEmpty()
        set(value) = prefs.edit { putString(KEY_DEEZER_QUALITY, value.trim()) }

    fun setDeezerSession(arl: String, accountName: String = "", tier: String = "FREE") {
        prefs.edit {
            putString(KEY_DEEZER_ARL, arl.trim())
            if (accountName.isNotBlank()) putString(KEY_DEEZER_ACCOUNT, accountName.trim())
            putString(KEY_DEEZER_TIER, tier.trim())
        }
        _deezerConfigured.value = arl.isNotBlank()
    }

    fun clearDeezerSession() {
        prefs.edit {
            remove(KEY_DEEZER_ARL)
            remove(KEY_DEEZER_ACCOUNT)
            remove(KEY_DEEZER_TIER)
            remove(KEY_DEEZER_QUALITY)
        }
        _deezerConfigured.value = false
    }

    val podcastIndexKey: String get() = prefs.getString(KEY_PODCASTINDEX_KEY, "").orEmpty()
    val podcastIndexSecret: String get() = prefs.getString(KEY_PODCASTINDEX_SECRET, "").orEmpty()

    private val _podcastIndexConfigured = MutableStateFlow(hasPodcastIndexCredentials())
    val podcastIndexConfigured: StateFlow<Boolean> = _podcastIndexConfigured.asStateFlow()

    fun setPodcastIndexCredentials(key: String, secret: String) {
        prefs.edit {
            putString(KEY_PODCASTINDEX_KEY, key.trim())
            putString(KEY_PODCASTINDEX_SECRET, secret.trim())
        }
        _podcastIndexConfigured.value = hasPodcastIndexCredentials()
    }

    private fun hasPodcastIndexCredentials() = podcastIndexKey.isNotBlank() && podcastIndexSecret.isNotBlank()

    /**
     * Re-reads every value from storage. After a restore that is what the backup wrote; after the
     * store is cleared it is each default, so signing out and restoring share this one path.
     */
    fun reloadFlows() {
        _navidromeConfigured.value = hasNavidromeCredentials()
        _ytMusicConfigured.value = ytMusicAuthCookie.isNotBlank()
        _deezerConfigured.value = deezerAuthArl.isNotBlank()
        _podcastIndexConfigured.value = hasPodcastIndexCredentials()
    }
}
