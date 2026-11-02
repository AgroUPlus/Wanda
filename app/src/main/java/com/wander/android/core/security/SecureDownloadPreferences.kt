package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the background download sweep may fetch, over which network, and how much disk it and the
 * streaming cache may take.
 *
 * Each default is what the app did before the setting existed: liked songs and the top smart mix
 * downloaded on Wi-Fi with no cap, and a 2 GB streaming cache.
 */
internal class SecureDownloadPreferences(private val prefs: SharedPreferences) {

    private val _autoDownloadLiked = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_LIKED, true))
    val autoDownloadLiked: StateFlow<Boolean> = _autoDownloadLiked.asStateFlow()

    /** Fill what is left of a batch with what `OfflinePredictor` expects next — songs the app predicts, not ones chosen. */
    private val _autoDownloadPredicted = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_PREDICTED, true))
    val autoDownloadPredicted: StateFlow<Boolean> = _autoDownloadPredicted.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    /** The most downloads may take up, in bytes; [UNLIMITED] for no cap. */
    private val _limitBytes = MutableStateFlow(prefs.getLong(KEY_DOWNLOAD_LIMIT_BYTES, UNLIMITED))
    val limitBytes: StateFlow<Long> = _limitBytes.asStateFlow()

    /** Read once, when the cache opens: a `SimpleCache` cannot be resized while it is in use. */
    private val _streamCacheBytes = MutableStateFlow(prefs.getLong(KEY_STREAM_CACHE_BYTES, DEFAULT_STREAM_CACHE_BYTES))
    val streamCacheBytes: StateFlow<Long> = _streamCacheBytes.asStateFlow()

    /**
     * Set when a download run stopped because the phone had no room left, and cleared by the next
     * track that fits. Periodic work keeps no output once it is rescheduled, so this is the one
     * record of why downloading went quiet. Device-local: another phone's disk says nothing here.
     */
    private val _storageFull = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOADS_STORAGE_FULL, false))
    val storageFull: StateFlow<Boolean> = _storageFull.asStateFlow()

    /**
     * Tracks the user downloaded by hand — from a track's menu, an album or playlist header, or a
     * selection. Doubles as the queue the manual download job works through: an id here whose
     * track is not downloaded yet is still waiting.
     *
     * Recorded so the cleanups that let go of downloads the app chose (stale downloads, a show's
     * keep-last-N episodes) never delete one the user asked for. Device-local, like the files.
     */
    private val _manualTrackIds = MutableStateFlow(readManualTrackIds())
    val manualTrackIds: StateFlow<Set<String>> = _manualTrackIds.asStateFlow()

    fun markManual(trackIds: Collection<String>) = updateManual { it + trackIds }

    fun unmarkManual(trackIds: Collection<String>) = updateManual { it - trackIds.toSet() }

    /** Serialised: a selection's download and a worker's cleanup can land at the same moment. */
    @Synchronized
    private fun updateManual(change: (Set<String>) -> Set<String>) {
        val next = change(_manualTrackIds.value)
        if (next == _manualTrackIds.value) return
        prefs.edit { putStringSet(KEY_MANUAL_DOWNLOAD_IDS, next) }
        _manualTrackIds.value = next
    }

    // Copied: the set `getStringSet` returns belongs to the preferences and must not be kept.
    private fun readManualTrackIds(): Set<String> =
        prefs.getStringSet(KEY_MANUAL_DOWNLOAD_IDS, emptySet()).orEmpty().toSet()

    fun setStorageFull(full: Boolean) {
        if (_storageFull.value == full) return
        prefs.edit { putBoolean(KEY_DOWNLOADS_STORAGE_FULL, full) }
        _storageFull.value = full
    }

    fun setAutoDownloadLiked(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD_LIKED, enabled) }
        _autoDownloadLiked.value = enabled
    }

    fun setAutoDownloadPredicted(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD_PREDICTED, enabled) }
        _autoDownloadPredicted.value = enabled
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DOWNLOAD_WIFI_ONLY, enabled) }
        _wifiOnly.value = enabled
    }

    fun setLimitBytes(bytes: Long) {
        prefs.edit { putLong(KEY_DOWNLOAD_LIMIT_BYTES, bytes) }
        _limitBytes.value = bytes
    }

    fun setStreamCacheBytes(bytes: Long) {
        prefs.edit { putLong(KEY_STREAM_CACHE_BYTES, bytes) }
        _streamCacheBytes.value = bytes
    }

    /** See [SecureDisplayPreferences.reloadFlows]. */
    fun reloadFlows() {
        _autoDownloadLiked.value = prefs.getBoolean(KEY_AUTO_DOWNLOAD_LIKED, true)
        _autoDownloadPredicted.value = prefs.getBoolean(KEY_AUTO_DOWNLOAD_PREDICTED, true)
        _wifiOnly.value = prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true)
        _limitBytes.value = prefs.getLong(KEY_DOWNLOAD_LIMIT_BYTES, UNLIMITED)
        _streamCacheBytes.value = prefs.getLong(KEY_STREAM_CACHE_BYTES, DEFAULT_STREAM_CACHE_BYTES)
        _storageFull.value = prefs.getBoolean(KEY_DOWNLOADS_STORAGE_FULL, false)
        _manualTrackIds.value = readManualTrackIds()
    }

    companion object {
        const val UNLIMITED = 0L
        const val DEFAULT_STREAM_CACHE_BYTES = 2L * 1024 * 1024 * 1024
    }
}
