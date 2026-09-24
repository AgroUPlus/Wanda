package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio, network playback, and offline mode preferences stored in encrypted storage.
 */
internal class SecurePlaybackPreferences(private val prefs: SharedPreferences) {

    private val _isOfflineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isPreloadNextEnabled = MutableStateFlow(prefs.getBoolean(KEY_PRELOAD_NEXT, true))
    val isPreloadNextEnabled: StateFlow<Boolean> = _isPreloadNextEnabled.asStateFlow()

    private val _isSkipSilenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, false))
    val isSkipSilenceEnabled: StateFlow<Boolean> = _isSkipSilenceEnabled.asStateFlow()

    private val _isIndexOnMobileDataEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_INDEX_ON_MOBILE_DATA, false))
    val isIndexOnMobileDataEnabled: StateFlow<Boolean> = _isIndexOnMobileDataEnabled.asStateFlow()

    private val _isRadioMode = MutableStateFlow(prefs.getBoolean(KEY_RADIO_MODE, true))
    val isRadioMode: StateFlow<Boolean> = _isRadioMode.asStateFlow()

    private val _isExternalLyricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_EXTERNAL_LYRICS, true))
    val isExternalLyricsEnabled: StateFlow<Boolean> = _isExternalLyricsEnabled.asStateFlow()

    private val _isAutoUpdateCheckEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false))
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    private val _isArtistReleaseNotificationEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_RELEASE_NOTIFICATIONS, false))
    val isArtistReleaseNotificationEnabled: StateFlow<Boolean> =
        _isArtistReleaseNotificationEnabled.asStateFlow()

    var preferredAudioLanguage: String?
        get() = prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGE, null)
        set(value) = prefs.edit { putString(KEY_PREFERRED_AUDIO_LANGUAGE, value) }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OFFLINE_MODE, enabled) }
        _isOfflineMode.value = enabled
    }

    fun setPreloadNextEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PRELOAD_NEXT, enabled) }
        _isPreloadNextEnabled.value = enabled
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SKIP_SILENCE, enabled) }
        _isSkipSilenceEnabled.value = enabled
    }

    fun setIndexOnMobileDataEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INDEX_ON_MOBILE_DATA, enabled) }
        _isIndexOnMobileDataEnabled.value = enabled
    }

    fun setRadioMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RADIO_MODE, enabled) }
        _isRadioMode.value = enabled
    }

    fun setExternalLyricsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EXTERNAL_LYRICS, enabled) }
        _isExternalLyricsEnabled.value = enabled
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_UPDATE_CHECK, enabled) }
        _isAutoUpdateCheckEnabled.value = enabled
    }

    fun setArtistReleaseNotificationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RELEASE_NOTIFICATIONS, enabled) }
        _isArtistReleaseNotificationEnabled.value = enabled
    }

    fun resetFlows() {
        _isOfflineMode.value = false
        _isPreloadNextEnabled.value = true
        _isSkipSilenceEnabled.value = false
        _isIndexOnMobileDataEnabled.value = false
        _isRadioMode.value = true
        _isExternalLyricsEnabled.value = true
        _isAutoUpdateCheckEnabled.value = false
        _isArtistReleaseNotificationEnabled.value = false
    }
}

