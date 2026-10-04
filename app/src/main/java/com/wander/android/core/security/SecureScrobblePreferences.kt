package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A scrobbling account: who it is, and whether plays are being sent to it right now. */
data class ScrobbleAccount(val username: String?, val enabled: Boolean) {
    val isConnected: Boolean get() = username != null
}

/**
 * The scrobbling services this device forwards plays to: ListenBrainz and Last.fm.
 *
 * Connecting is the consent — nothing is sent to either until an account is connected here — and
 * each has its own switch to pause it without signing out. The token and session are credentials
 * and live only in this encrypted store; they are in `ACCOUNT_KEYS`, so a backup carries them only
 * when sign-ins were chosen.
 */
internal class SecureScrobblePreferences(private val prefs: SharedPreferences) {

    private val _listenBrainz = MutableStateFlow(read(KEY_LISTENBRAINZ_USER, KEY_LISTENBRAINZ_ENABLED))
    val listenBrainz: StateFlow<ScrobbleAccount> = _listenBrainz.asStateFlow()

    private val _lastFm = MutableStateFlow(read(KEY_LASTFM_USER, KEY_LASTFM_ENABLED))
    val lastFm: StateFlow<ScrobbleAccount> = _lastFm.asStateFlow()

    val listenBrainzToken: String? get() = prefs.getString(KEY_LISTENBRAINZ_TOKEN, null)
    val lastFmSession: String? get() = prefs.getString(KEY_LASTFM_SESSION, null)

    /** A key and secret the user registered themselves, when this build ships none. */
    val lastFmApiKey: String? get() = prefs.getString(KEY_LASTFM_API_KEY, null)?.takeIf { it.isNotBlank() }
    val lastFmApiSecret: String? get() = prefs.getString(KEY_LASTFM_API_SECRET, null)?.takeIf { it.isNotBlank() }

    fun setLastFmApp(key: String, secret: String) = prefs.edit {
        putString(KEY_LASTFM_API_KEY, key.trim())
        putString(KEY_LASTFM_API_SECRET, secret.trim())
    }

    fun connectListenBrainz(username: String, token: String) {
        prefs.edit {
            putString(KEY_LISTENBRAINZ_USER, username)
            putString(KEY_LISTENBRAINZ_TOKEN, token)
            putBoolean(KEY_LISTENBRAINZ_ENABLED, true)
        }
        _listenBrainz.value = read(KEY_LISTENBRAINZ_USER, KEY_LISTENBRAINZ_ENABLED)
    }

    fun connectLastFm(username: String, session: String) {
        prefs.edit {
            putString(KEY_LASTFM_USER, username)
            putString(KEY_LASTFM_SESSION, session)
            putBoolean(KEY_LASTFM_ENABLED, true)
        }
        _lastFm.value = read(KEY_LASTFM_USER, KEY_LASTFM_ENABLED)
    }

    fun setListenBrainzEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LISTENBRAINZ_ENABLED, enabled) }
        _listenBrainz.value = read(KEY_LISTENBRAINZ_USER, KEY_LISTENBRAINZ_ENABLED)
    }

    fun setLastFmEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LASTFM_ENABLED, enabled) }
        _lastFm.value = read(KEY_LASTFM_USER, KEY_LASTFM_ENABLED)
    }

    /** Signs out: the token goes, not just the switch. */
    fun disconnectListenBrainz() {
        prefs.edit { remove(KEY_LISTENBRAINZ_TOKEN); remove(KEY_LISTENBRAINZ_USER); remove(KEY_LISTENBRAINZ_ENABLED) }
        _listenBrainz.value = ScrobbleAccount(null, false)
    }

    fun disconnectLastFm() {
        prefs.edit { remove(KEY_LASTFM_SESSION); remove(KEY_LASTFM_USER); remove(KEY_LASTFM_ENABLED) }
        _lastFm.value = ScrobbleAccount(null, false)
    }

    /**
     * Re-reads every value from storage. After a restore that is what the backup wrote; after the
     * store is cleared it is each default, so signing out and restoring share this one path.
     */
    fun reloadFlows() {
        _listenBrainz.value = read(KEY_LISTENBRAINZ_USER, KEY_LISTENBRAINZ_ENABLED)
        _lastFm.value = read(KEY_LASTFM_USER, KEY_LASTFM_ENABLED)
    }

    private fun read(userKey: String, enabledKey: String): ScrobbleAccount {
        val user = prefs.getString(userKey, null)
        return ScrobbleAccount(user, user != null && prefs.getBoolean(enabledKey, false))
    }
}
