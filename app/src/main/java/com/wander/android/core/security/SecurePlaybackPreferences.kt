package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wander.android.data.model.PlaybackMediaType
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

    /**
     * Default `false`, unlike lyrics above: this is a brand-new outbound call to a third party
     * (musicbrainz.org) that nothing in the app made before, so it starts off rather than on.
     */
    private val _isMusicBrainzLookupEnabled = MutableStateFlow(prefs.getBoolean(KEY_MUSICBRAINZ_LOOKUP, false))
    val isMusicBrainzLookupEnabled: StateFlow<Boolean> = _isMusicBrainzLookupEnabled.asStateFlow()

    /** Off until the listener turns it on: podcast searches go to api.podcastindex.org, which nothing else did. */
    private val _isPodcastIndexEnabled = MutableStateFlow(prefs.getBoolean(KEY_PODCASTINDEX_ENABLED, false))
    val isPodcastIndexEnabled: StateFlow<Boolean> = _isPodcastIndexEnabled.asStateFlow()

    private val _isAutoUpdateCheckEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false))
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    private val _isArtistReleaseNotificationEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_RELEASE_NOTIFICATIONS, false))
    val isArtistReleaseNotificationEnabled: StateFlow<Boolean> =
        _isArtistReleaseNotificationEnabled.asStateFlow()

    private val _preferredMediaType = MutableStateFlow(
        PlaybackMediaType.entries.firstOrNull { it.name == prefs.getString(KEY_PREFERRED_MEDIA_TYPE, null) }
            ?: PlaybackMediaType.SONG
    )
    val preferredMediaType: StateFlow<PlaybackMediaType> = _preferredMediaType.asStateFlow()

    fun setPreferredMediaType(type: PlaybackMediaType) {
        prefs.edit { putString(KEY_PREFERRED_MEDIA_TYPE, type.name) }
        _preferredMediaType.value = type
    }

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

    fun setMusicBrainzLookupEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MUSICBRAINZ_LOOKUP, enabled) }
        _isMusicBrainzLookupEnabled.value = enabled
    }

    fun setPodcastIndexEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PODCASTINDEX_ENABLED, enabled) }
        _isPodcastIndexEnabled.value = enabled
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_UPDATE_CHECK, enabled) }
        _isAutoUpdateCheckEnabled.value = enabled
    }

    fun setArtistReleaseNotificationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RELEASE_NOTIFICATIONS, enabled) }
        _isArtistReleaseNotificationEnabled.value = enabled
    }

    /**
     * Re-reads every value from storage. After a restore that is what the backup wrote; after the
     * store is cleared it is each default, so signing out and restoring share this one path.
     */
    fun reloadFlows() {
        _isOfflineMode.value = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        _isPreloadNextEnabled.value = prefs.getBoolean(KEY_PRELOAD_NEXT, true)
        _isSkipSilenceEnabled.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        _isIndexOnMobileDataEnabled.value = prefs.getBoolean(KEY_INDEX_ON_MOBILE_DATA, false)
        _isRadioMode.value = prefs.getBoolean(KEY_RADIO_MODE, true)
        _isExternalLyricsEnabled.value = prefs.getBoolean(KEY_EXTERNAL_LYRICS, true)
        _isMusicBrainzLookupEnabled.value = prefs.getBoolean(KEY_MUSICBRAINZ_LOOKUP, false)
        _isPodcastIndexEnabled.value = prefs.getBoolean(KEY_PODCASTINDEX_ENABLED, false)
        _isAutoUpdateCheckEnabled.value = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false)
        _isArtistReleaseNotificationEnabled.value = prefs.getBoolean(KEY_RELEASE_NOTIFICATIONS, false)
        _preferredMediaType.value =
            PlaybackMediaType.entries.firstOrNull { it.name == prefs.getString(KEY_PREFERRED_MEDIA_TYPE, null) }
                ?: PlaybackMediaType.SONG
    }
}

