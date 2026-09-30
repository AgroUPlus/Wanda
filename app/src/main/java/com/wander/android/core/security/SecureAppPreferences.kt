package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages local scan watermarks, setup completion, sharing domains, and background job states.
 */
internal class SecureAppPreferences(private val prefs: SharedPreferences) {

    var lastSeenReleaseWatermark: Long
        get() = prefs.getLong(KEY_RELEASE_WATERMARK, 0L)
        set(value) = prefs.edit { putLong(KEY_RELEASE_WATERMARK, value) }

    var duplicateScanCursor: String
        get() = prefs.getString(KEY_DUPLICATE_SCAN_CURSOR, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DUPLICATE_SCAN_CURSOR, value) }

    var lastNotifiedRelease: String
        get() = prefs.getString(KEY_LAST_NOTIFIED_RELEASE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST_NOTIFIED_RELEASE, value) }

    var lastSeenReplayYear: Int
        get() = prefs.getInt(KEY_REPLAY_SEEN_YEAR, 0)
        set(value) = prefs.edit { putInt(KEY_REPLAY_SEEN_YEAR, value) }

    var isIncognitoMode: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO, false)
        set(value) = prefs.edit { putBoolean(KEY_INCOGNITO, value) }

    private val _hasCompletedSetup = MutableStateFlow(prefs.getBoolean(KEY_SETUP_DONE, false))
    val hasCompletedSetup: StateFlow<Boolean> = _hasCompletedSetup.asStateFlow()

    fun markSetupComplete() {
        prefs.edit { putBoolean(KEY_SETUP_DONE, true) }
        _hasCompletedSetup.value = true
    }

    var localScanWatermark: Long
        get() = prefs.getLong(KEY_LOCAL_WATERMARK, 0L)
        set(value) = prefs.edit { putLong(KEY_LOCAL_WATERMARK, value) }

    var pendingForget: Set<String>
        get() = prefs.getStringSet(KEY_PENDING_FORGET, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_PENDING_FORGET, value) }

    var localScanFolder: String?
        get() = prefs.getString(KEY_LOCAL_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_LOCAL_FOLDER, value) }

    var localScanFolderLabel: String?
        get() = prefs.getString(KEY_LOCAL_FOLDER_LABEL, null)
        set(value) = prefs.edit { putString(KEY_LOCAL_FOLDER_LABEL, value) }

    private val workPausedFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()

    @Synchronized
    fun workPaused(kindName: String): StateFlow<Boolean> =
        workPausedFlows.getOrPut(kindName) {
            MutableStateFlow(prefs.getBoolean(workPausedKey(kindName), false))
        }.asStateFlow()

    @Synchronized
    fun setWorkPaused(kindName: String, paused: Boolean) {
        prefs.edit { putBoolean(workPausedKey(kindName), paused) }
        workPausedFlows.getOrPut(kindName) { MutableStateFlow(paused) }.value = paused
    }

    private fun workPausedKey(kindName: String) = "key_work_paused_$kindName"

    private val _shareDomain = MutableStateFlow(prefs.getString(KEY_SHARE_DOMAIN, "").orEmpty())
    val shareDomain: StateFlow<String> = _shareDomain.asStateFlow()

    private val _agroShareDomain = MutableStateFlow(prefs.getString(KEY_AGRO_SHARE_DOMAIN, "").orEmpty())
    val agroShareDomain: StateFlow<String> = _agroShareDomain.asStateFlow()

    var agroShareHosts: String
        get() = prefs.getString(KEY_AGRO_SHARE_HOSTS, "").orEmpty()
        private set(value) = prefs.edit { putString(KEY_AGRO_SHARE_HOSTS, value) }

    fun setAgroShareSettings(domain: String, hosts: String) {
        prefs.edit { putString(KEY_AGRO_SHARE_DOMAIN, domain.trim().lowercase()) }
        agroShareHosts = hosts
        _agroShareDomain.value = domain.trim().lowercase()
    }

    fun setShareDomain(domain: String) {
        val host = domain.trim()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .lowercase()
            .takeIf { it.matches(HOST_REGEX) }
            .orEmpty()
        prefs.edit { putString(KEY_SHARE_DOMAIN, host) }
        _shareDomain.value = host
    }

    fun resetFlows() {
        _hasCompletedSetup.value = false
        _shareDomain.value = ""
        _agroShareDomain.value = ""
    }
}
