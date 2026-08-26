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

    /**
     * Endless-radio queue top-up. Persisted because it was an in-memory flag on `PlayerConnection`,
     * so a mode the user had deliberately turned on silently reset on every launch.
     */
    private val _isRadioMode = MutableStateFlow(prefs.getBoolean(KEY_RADIO_MODE, false))
    val isRadioMode: StateFlow<Boolean> = _isRadioMode.asStateFlow()

    private val _isAmoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val isAmoledBlack: StateFlow<Boolean> = _isAmoledBlack.asStateFlow()

    private val _isMonetDynamic = MutableStateFlow(prefs.getBoolean(KEY_MONET_DYNAMIC, true))
    val isMonetDynamic: StateFlow<Boolean> = _isMonetDynamic.asStateFlow()

    /** Off by default: a version check on every launch is a network call the user did not ask for. */
    private val _isAutoUpdateCheckEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false))
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

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

    /**
     * The one folder the on-device scan is allowed to look in, as a MediaStore `RELATIVE_PATH`
     * prefix ending in `/` — for example `Music/Vinyl rips/`.
     *
     * Null means the whole volume, which stays the default. A phone's audio is not all music:
     * ringtones, podcast downloads, voice memos and whatever a messaging app saved all satisfy
     * `IS_MUSIC`, and on a full device the library is mostly things nobody wants to see.
     *
     * Stored as the relative path rather than the picked tree URI because that is what MediaStore
     * can be queried against; the URI is kept alongside it only so the row can name the folder.
     */
    var localScanFolder: String?
        get() = prefs.getString(KEY_LOCAL_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_LOCAL_FOLDER, value) }

    /** The picked tree URI, shown in Settings. Not used for querying — see [localScanFolder]. */
    var localScanFolderLabel: String?
        get() = prefs.getString(KEY_LOCAL_FOLDER_LABEL, null)
        set(value) = prefs.edit { putString(KEY_LOCAL_FOLDER_LABEL, value) }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OFFLINE_MODE, enabled) }
        _isOfflineMode.value = enabled
    }

    fun setRadioMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RADIO_MODE, enabled) }
        _isRadioMode.value = enabled
    }

    fun setAmoledBlack(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_BLACK, enabled) }
        _isAmoledBlack.value = enabled
    }

    fun setMonetDynamic(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MONET_DYNAMIC, enabled) }
        _isMonetDynamic.value = enabled
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_UPDATE_CHECK, enabled) }
        _isAutoUpdateCheckEnabled.value = enabled
    }

    // ── Sharing ─────────────────────────────────────────────────────────────────────────────

    /**
     * A domain of the user's own to send share links through — `frwd.top` — or blank to share each
     * backend's own link untouched.
     *
     * Stored as a bare host: whatever is typed, scheme, path and case are stripped, so the value
     * can only ever be used to build one shape of URL.
     */
    private val _shareDomain = MutableStateFlow(prefs.getString(KEY_SHARE_DOMAIN, "").orEmpty())
    val shareDomain: StateFlow<String> = _shareDomain.asStateFlow()

    /**
     * The same setting as configured on a paired Agro server, cached here so it survives a restart
     * and works with the server unreachable.
     *
     * Kept apart from [shareDomain] rather than overwriting it: Agro is optional, and unpairing
     * must leave the user with the domain *they* typed, not with whatever the server last said.
     * Blank whenever Agro is unpaired, has no domain, or has the feature switched off.
     */
    private val _agroShareDomain = MutableStateFlow(prefs.getString(KEY_AGRO_SHARE_DOMAIN, "").orEmpty())
    val agroShareDomain: StateFlow<String> = _agroShareDomain.asStateFlow()

    /** Extra hosts the server will forward to, comma separated, as it reported them. */
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
            .takeIf { it.matches(HOST) }
            .orEmpty()
        prefs.edit { putString(KEY_SHARE_DOMAIN, host) }
        _shareDomain.value = host
    }

    /**
     * `clear()` wipes preferences as well as credentials, so **every** flow has to be reset to the
     * value the store now actually holds. Leaving some of them stale meant a wipe left the app
     * showing a paired Agro server and the previous theme until the next cold start.
     */
    fun clearAllCredentials() {
        val deviceId = prefs.getString(KEY_AGRO_DEVICE_ID, null)
        prefs.edit {
            clear()
            deviceId?.let { putString(KEY_AGRO_DEVICE_ID, it) }
        }
        _isOfflineMode.value = false
        _isRadioMode.value = false
        _navidromeConfigured.value = false
        _ytMusicConfigured.value = false
        _hasCompletedSetup.value = false
        _isAmoledBlack.value = false
        _isMonetDynamic.value = true
        _agroConfigured.value = false
        _agroSyncSettings.value = false
        _shareDomain.value = ""
        _agroShareDomain.value = ""
        _isAutoUpdateCheckEnabled.value = false
    }

    private val _agroConfigured = MutableStateFlow(hasAgroCredentials())
    val agroConfigured: StateFlow<Boolean> = _agroConfigured.asStateFlow()

    // ── Agro Server Integration ──────────────────────────────────────────────────────────────

    val agroServerUrl: String get() = prefs.getString(KEY_AGRO_URL, "").orEmpty()
    val agroApiKey: String get() = prefs.getString(KEY_AGRO_KEY, "").orEmpty()
    val agroUsername: String get() = prefs.getString(KEY_AGRO_USER, "").orEmpty()
    val agroDevicePetname: String get() = prefs.getString(KEY_AGRO_PETNAME, "").orEmpty()
    /**
     * This device's stable identity to Agro, generated once and kept forever.
     *
     * It used to be derived from the hardware — `"wanda-" + Build.MODEL` — which meant two of the
     * same phone on one account were literally the same device to the server. Harmless enough for
     * a single playback handoff; fatal for a per-device library index, where it would merge two
     * collections into one and then offer each phone the other's missing tracks.
     *
     * Generated lazily rather than at construction so it costs nothing until Agro is used, and an
     * id already stored — including an old model-derived one — is kept, so pairing survives the
     * upgrade.
     */
    val agroDeviceId: String
        get() = prefs.getString(KEY_AGRO_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: ("wanda-" + java.util.UUID.randomUUID().toString().take(12)).also { generated ->
                prefs.edit { putString(KEY_AGRO_DEVICE_ID, generated) }
            }

    /**
     * [deviceId] defaults to whatever this device already answers to, so pairing again — or
     * re-pairing against a different server — does not mint a new identity and orphan everything
     * the old one reported.
     */
    fun setAgroCredentials(
        url: String,
        username: String,
        apiKey: String,
        deviceId: String = agroDeviceId
    ) {
        prefs.edit {
            putString(KEY_AGRO_URL, url.trim().trimEnd('/'))
            putString(KEY_AGRO_USER, username.trim())
            putString(KEY_AGRO_KEY, apiKey.trim())
            putString(KEY_AGRO_DEVICE_ID, deviceId.trim())
        }
        _agroConfigured.value = hasAgroCredentials()
    }

    /**
     * Whether this device uploads its local music to Agro.
     *
     * Off by default and never inferred from pairing: sending a music library to a server is the
     * user's decision, and pairing was for playback handoff.
     */
    private val _agroLibrarySync = MutableStateFlow(prefs.getBoolean(KEY_AGRO_LIBRARY_SYNC, false))
    val agroLibrarySyncFlow: StateFlow<Boolean> = _agroLibrarySync.asStateFlow()
    val agroLibrarySync: Boolean get() = _agroLibrarySync.value

    fun setAgroLibrarySync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_LIBRARY_SYNC, enabled) }
        _agroLibrarySync.value = enabled
    }

    fun setAgroDevicePetname(petname: String) {
        prefs.edit { putString(KEY_AGRO_PETNAME, petname.trim()) }
    }

    private val _agroSyncSettings = MutableStateFlow(prefs.getBoolean(KEY_AGRO_SYNC_SETTINGS, false))
    val agroSyncSettings: StateFlow<Boolean> = _agroSyncSettings.asStateFlow()

    fun setAgroSyncSettings(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_SYNC_SETTINGS, enabled) }
        _agroSyncSettings.value = enabled
    }

    fun clearAgroCredentials() {
        prefs.edit {
            remove(KEY_AGRO_URL); remove(KEY_AGRO_USER); remove(KEY_AGRO_KEY); remove(KEY_AGRO_PETNAME)
        }
        _agroConfigured.value = false
    }

    /**
     * The username is part of the credential, not decoration.
     *
     * Every account-scoped field in Agro's schema names a `userId`, and the server checks it against
     * the identity the token resolved to. A stored server and token with no username reported this
     * app as paired while every query it could send was refused, which looked like the server being
     * broken rather than the pairing being incomplete.
     */
    private fun hasAgroCredentials() =
        agroServerUrl.isNotBlank() && agroApiKey.isNotBlank() && agroUsername.isNotBlank()

    companion object {
        private const val PREFS_NAME = "wanda_secure_vault"
        private const val KEY_NAVIDROME_URL = "key_navidrome_url"
        private const val KEY_NAVIDROME_USER = "key_navidrome_user"
        private const val KEY_NAVIDROME_TOKEN = "key_navidrome_token"
        private const val KEY_YTM_COOKIE = "key_ytm_cookie"
        private const val KEY_YTM_VISITOR = "key_ytm_visitor"
        private const val KEY_AGRO_URL = "key_agro_url"
        private const val KEY_AGRO_USER = "key_agro_user"
        private const val KEY_AGRO_KEY = "key_agro_key"
        private const val KEY_AGRO_PETNAME = "key_agro_petname"
        private const val KEY_AGRO_SYNC_SETTINGS = "key_agro_sync_settings"
        private const val KEY_AGRO_DEVICE_ID = "key_agro_device_id"
        private const val KEY_AGRO_LIBRARY_SYNC = "key_agro_library_sync"
        private const val KEY_OFFLINE_MODE = "key_offline_mode"
        private const val KEY_RADIO_MODE = "key_radio_mode"
        private const val KEY_AMOLED_BLACK = "key_amoled_black"
        private const val KEY_MONET_DYNAMIC = "key_monet_dynamic"
        private const val KEY_AUTO_UPDATE_CHECK = "key_auto_update_check"
        private const val KEY_INCOGNITO = "key_incognito"
        private const val KEY_LOCAL_WATERMARK = "key_local_scan_watermark"
        private const val KEY_LOCAL_FOLDER = "key_local_scan_folder"
        private const val KEY_LOCAL_FOLDER_LABEL = "key_local_scan_folder_label"
        private const val KEY_SETUP_DONE = "key_setup_complete"
        private const val KEY_SHARE_DOMAIN = "key_share_domain"
        private const val KEY_AGRO_SHARE_DOMAIN = "key_agro_share_domain"
        private const val KEY_AGRO_SHARE_HOSTS = "key_agro_share_hosts"

        /** A bare hostname: labels, dots, and a TLD. Anything else is not a domain to build on. */
        private val HOST = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""")

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
