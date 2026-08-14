package com.wander.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only place credentials live. Backed by the Android Keystore via
 * [EncryptedSharedPreferences] — nothing here is ever written to Room, to logs, or to backups.
 *
 * Construct through Hilt ([com.wander.android.di.AppModule]); [create] exists for that binding
 * and for tests.
 */
class SecureStorage private constructor(private val prefs: SharedPreferences) {

    private val _isOfflineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isAmoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val isAmoledBlack: StateFlow<Boolean> = _isAmoledBlack.asStateFlow()

    private val _isMonetDynamic = MutableStateFlow(prefs.getBoolean(KEY_MONET_DYNAMIC, true))
    val isMonetDynamic: StateFlow<Boolean> = _isMonetDynamic.asStateFlow()

    private val _navidromeConfigured = MutableStateFlow(hasNavidromeCredentials())
    val navidromeConfigured: StateFlow<Boolean> = _navidromeConfigured.asStateFlow()

    private val _ytMusicConfigured = MutableStateFlow(ytMusicAuthCookie.isNotBlank())
    val ytMusicConfigured: StateFlow<Boolean> = _ytMusicConfigured.asStateFlow()

    // ── Navidrome / Subsonic ────────────────────────────────────────────────────────────────

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
            remove(KEY_NAVIDROME_URL); remove(KEY_NAVIDROME_USER); remove(KEY_NAVIDROME_TOKEN)
        }
        _navidromeConfigured.value = false
    }

    private fun hasNavidromeCredentials() =
        navidromeServerUrl.isNotBlank() && navidromeUsername.isNotBlank() && navidromePassword.isNotBlank()

    // ── YouTube Music ───────────────────────────────────────────────────────────────────────

    val ytMusicAuthCookie: String get() = prefs.getString(KEY_YTM_COOKIE, "").orEmpty()
    val ytMusicVisitorData: String get() = prefs.getString(KEY_YTM_VISITOR, "").orEmpty()

    fun setYtMusicSession(cookie: String, visitorData: String = ytMusicVisitorData) {
        prefs.edit {
            putString(KEY_YTM_COOKIE, cookie.trim())
            putString(KEY_YTM_VISITOR, visitorData)
        }
        _ytMusicConfigured.value = cookie.isNotBlank()
    }

    fun clearYtMusicSession() {
        prefs.edit { remove(KEY_YTM_COOKIE); remove(KEY_YTM_VISITOR) }
        _ytMusicConfigured.value = false
    }

    // ── Preferences ─────────────────────────────────────────────────────────────────────────

    var isIncognitoMode: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO, false)
        set(value) = prefs.edit { putBoolean(KEY_INCOGNITO, value) }

    private val _hasCompletedSetup = MutableStateFlow(prefs.getBoolean(KEY_SETUP_DONE, false))

    /** False until the welcome flow has been seen, whether it was completed or skipped. */
    val hasCompletedSetup: StateFlow<Boolean> = _hasCompletedSetup.asStateFlow()

    fun markSetupComplete() {
        prefs.edit { putBoolean(KEY_SETUP_DONE, true) }
        _hasCompletedSetup.value = true
    }

    /** MediaStore `DATE_MODIFIED` watermark, so a rescan only reads what changed. */
    var localScanWatermark: Long
        get() = prefs.getLong(KEY_LOCAL_WATERMARK, 0L)
        set(value) = prefs.edit { putLong(KEY_LOCAL_WATERMARK, value) }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OFFLINE_MODE, enabled) }
        _isOfflineMode.value = enabled
    }

    fun setAmoledBlack(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_BLACK, enabled) }
        _isAmoledBlack.value = enabled
    }

    fun setMonetDynamic(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MONET_DYNAMIC, enabled) }
        _isMonetDynamic.value = enabled
    }

    fun clearAllCredentials() {
        prefs.edit { clear() }
        _isOfflineMode.value = false
        _navidromeConfigured.value = false
        _ytMusicConfigured.value = false
        _hasCompletedSetup.value = false
    }

    companion object {
        private const val PREFS_NAME = "wanda_secure_vault"
        private const val KEY_NAVIDROME_URL = "key_navidrome_url"
        private const val KEY_NAVIDROME_USER = "key_navidrome_user"
        private const val KEY_NAVIDROME_TOKEN = "key_navidrome_token"
        private const val KEY_YTM_COOKIE = "key_ytm_cookie"
        private const val KEY_YTM_VISITOR = "key_ytm_visitor"
        private const val KEY_OFFLINE_MODE = "key_offline_mode"
        private const val KEY_AMOLED_BLACK = "key_amoled_black"
        private const val KEY_MONET_DYNAMIC = "key_monet_dynamic"
        private const val KEY_INCOGNITO = "key_incognito"
        private const val KEY_LOCAL_WATERMARK = "key_local_scan_watermark"
        private const val KEY_SETUP_DONE = "key_setup_complete"

        fun create(context: Context): SecureStorage {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return SecureStorage(prefs)
        }
    }
}
