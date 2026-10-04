package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages Agro server credentials, pairing state, capabilities, and sync toggles
 * stored in encrypted preferences.
 */
internal class SecureAgroPreferences(private val prefs: SharedPreferences) {

    private val _agroConfigured = MutableStateFlow(hasAgroCredentials())
    val agroConfigured: StateFlow<Boolean> = _agroConfigured.asStateFlow()

    val agroServerUrl: String get() = prefs.getString(KEY_AGRO_URL, "").orEmpty()
    val agroApiKey: String get() = prefs.getString(KEY_AGRO_KEY, "").orEmpty()
    val agroUsername: String get() = prefs.getString(KEY_AGRO_USER, "").orEmpty()
    val agroDevicePetname: String get() = prefs.getString(KEY_AGRO_PETNAME, "").orEmpty()

    var catalogCursor: Long
        get() = prefs.getLong(KEY_CATALOG_CURSOR, 0L)
        set(value) = prefs.edit { putLong(KEY_CATALOG_CURSOR, value) }

    var catalogLastPublishedAt: Long
        get() = prefs.getLong(KEY_CATALOG_PUBLISHED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_CATALOG_PUBLISHED_AT, value) }

    /** The tie-breaker for [catalogLastPublishedAt] — see `TrackEmbeddingDao.computedSince`. */
    var catalogLastPublishedTrack: String
        get() = prefs.getString(KEY_CATALOG_PUBLISHED_TRACK, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CATALOG_PUBLISHED_TRACK, value) }

    var agroCapabilities: Set<String>
        get() = prefs.getStringSet(KEY_AGRO_CAPABILITIES, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_AGRO_CAPABILITIES, value) }

    fun serverSupports(capability: String): Boolean = capability in agroCapabilities

    val agroDeviceId: String
        get() = prefs.getString(KEY_AGRO_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: ("wanda-" + UUID.randomUUID().toString().take(12)).also { generated ->
                prefs.edit { putString(KEY_AGRO_DEVICE_ID, generated) }
            }

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

    private val _agroP2pSync = MutableStateFlow(prefs.getBoolean(KEY_AGRO_P2P_SYNC, true))
    val agroP2pSyncFlow: StateFlow<Boolean> = _agroP2pSync.asStateFlow()
    val agroP2pSync: Boolean get() = _agroP2pSync.value

    fun setAgroP2pSync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_P2P_SYNC, enabled) }
        _agroP2pSync.value = enabled
        _agroLibrarySync.value = enabled || agroServerArchive
    }

    private val _agroPopularityContribution =
        MutableStateFlow(prefs.getBoolean(KEY_AGRO_POPULARITY, true))
    val agroPopularityContributionFlow: StateFlow<Boolean> = _agroPopularityContribution.asStateFlow()
    val agroPopularityContribution: Boolean get() = _agroPopularityContribution.value

    fun setAgroPopularityContribution(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_POPULARITY, enabled) }
        _agroPopularityContribution.value = enabled
    }

    private val _agroCatalogTrade = MutableStateFlow(prefs.getBoolean(KEY_AGRO_CATALOG_TRADE, false))
    val agroCatalogTradeFlow: StateFlow<Boolean> = _agroCatalogTrade.asStateFlow()
    val agroCatalogTrade: Boolean get() = _agroCatalogTrade.value

    fun setAgroCatalogTrade(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_CATALOG_TRADE, enabled) }
        _agroCatalogTrade.value = enabled
    }

    private val _agroServerArchive = MutableStateFlow(prefs.getBoolean(KEY_AGRO_SERVER_ARCHIVE, false))
    val agroServerArchiveFlow: StateFlow<Boolean> = _agroServerArchive.asStateFlow()
    val agroServerArchive: Boolean get() = _agroServerArchive.value

    fun setAgroServerArchive(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_SERVER_ARCHIVE, enabled) }
        _agroServerArchive.value = enabled
        _agroLibrarySync.value = agroP2pSync || enabled
    }

    private val _agroLibrarySync = MutableStateFlow(prefs.getBoolean(KEY_AGRO_LIBRARY_SYNC, true))
    val agroLibrarySyncFlow: StateFlow<Boolean> = _agroLibrarySync.asStateFlow()
    val agroLibrarySync: Boolean get() = _agroLibrarySync.value

    fun setAgroLibrarySync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_LIBRARY_SYNC, enabled) }
        _agroLibrarySync.value = enabled
    }

    private val _agroProxyEnabled = MutableStateFlow(prefs.getBoolean(KEY_AGRO_PROXY_ENABLED, true))
    val agroProxyEnabled: StateFlow<Boolean> = _agroProxyEnabled.asStateFlow()

    fun setAgroProxyEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_PROXY_ENABLED, enabled) }
        _agroProxyEnabled.value = enabled
    }

    fun setAgroDevicePetname(petname: String) {
        prefs.edit { putString(KEY_AGRO_PETNAME, petname.trim()) }
    }

    var agroVaultKey: ByteArray?
        get() = prefs.getString(KEY_AGRO_VAULT_KEY, null)?.let {
            runCatching { AgroVault.decodeBase64(it) }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_AGRO_VAULT_KEY)
            else putString(KEY_AGRO_VAULT_KEY, AgroVault.encodeBase64(value))
        }

    private val _agroSyncSettings = MutableStateFlow(prefs.getBoolean(KEY_AGRO_SYNC_SETTINGS, false))
    val agroSyncSettings: StateFlow<Boolean> = _agroSyncSettings.asStateFlow()

    fun setAgroSyncSettings(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_SYNC_SETTINGS, enabled) }
        _agroSyncSettings.value = enabled
    }

    /** Whether this device backs itself up to the Agro vault on its own, daily. Off until asked. */
    private val _cloudBackup = MutableStateFlow(prefs.getBoolean(KEY_AGRO_CLOUD_BACKUP, false))
    val cloudBackup: StateFlow<Boolean> = _cloudBackup.asStateFlow()

    fun setCloudBackup(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_CLOUD_BACKUP, enabled) }
        _cloudBackup.value = enabled
    }

    /** Whether those backups carry the sign-ins too. Off until the user accepts what that means. */
    private val _cloudBackupAccounts = MutableStateFlow(prefs.getBoolean(KEY_AGRO_CLOUD_BACKUP_ACCOUNTS, false))
    val cloudBackupAccounts: StateFlow<Boolean> = _cloudBackupAccounts.asStateFlow()

    fun setCloudBackupAccounts(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AGRO_CLOUD_BACKUP_ACCOUNTS, enabled) }
        _cloudBackupAccounts.value = enabled
    }

    var agroIdentityPrivateKey: String?
        get() = prefs.getString(KEY_AGRO_IDENTITY_PRIV, null)
        set(value) = prefs.edit { putString(KEY_AGRO_IDENTITY_PRIV, value) }

    var agroIdentityPublicKey: String?
        get() = prefs.getString(KEY_AGRO_IDENTITY_PUB, null)
        set(value) = prefs.edit { putString(KEY_AGRO_IDENTITY_PUB, value) }

    fun clearAgroCredentials() {
        prefs.edit {
            remove(KEY_AGRO_URL)
            remove(KEY_AGRO_USER)
            remove(KEY_AGRO_KEY)
            remove(KEY_AGRO_PETNAME)
            remove(KEY_AGRO_VAULT_KEY)
            remove(KEY_AGRO_IDENTITY_PRIV)
            remove(KEY_AGRO_IDENTITY_PUB)
            // The catalogue cursors are positions on *that* server. Kept, a different server
            // paired later would never be sent what was fingerprinted before it, and would be
            // read from the middle. Re-sending to the same one is harmless: it merges.
            remove(KEY_CATALOG_CURSOR)
            remove(KEY_CATALOG_PUBLISHED_AT)
            remove(KEY_CATALOG_PUBLISHED_TRACK)
            // Backing up to a server this device no longer belongs to is not something to resume
            // silently on the next one; the choice is asked again.
            remove(KEY_AGRO_CLOUD_BACKUP)
            remove(KEY_AGRO_CLOUD_BACKUP_ACCOUNTS)
        }
        _agroConfigured.value = false
        _cloudBackup.value = false
        _cloudBackupAccounts.value = false
    }

    /**
     * Re-reads every value from storage. After a restore that is what the backup wrote; after the
     * store is cleared it is each default, so signing out and restoring share this one path.
     */
    fun reloadFlows() {
        _agroConfigured.value = hasAgroCredentials()
        _agroP2pSync.value = prefs.getBoolean(KEY_AGRO_P2P_SYNC, true)
        _agroPopularityContribution.value = prefs.getBoolean(KEY_AGRO_POPULARITY, true)
        _agroCatalogTrade.value = prefs.getBoolean(KEY_AGRO_CATALOG_TRADE, false)
        _agroServerArchive.value = prefs.getBoolean(KEY_AGRO_SERVER_ARCHIVE, false)
        _agroLibrarySync.value = prefs.getBoolean(KEY_AGRO_LIBRARY_SYNC, true)
        _agroProxyEnabled.value = prefs.getBoolean(KEY_AGRO_PROXY_ENABLED, true)
        _agroSyncSettings.value = prefs.getBoolean(KEY_AGRO_SYNC_SETTINGS, false)
        _cloudBackup.value = prefs.getBoolean(KEY_AGRO_CLOUD_BACKUP, false)
        _cloudBackupAccounts.value = prefs.getBoolean(KEY_AGRO_CLOUD_BACKUP_ACCOUNTS, false)
    }

    private fun hasAgroCredentials() =
        agroServerUrl.isNotBlank() && agroApiKey.isNotBlank() && agroUsername.isNotBlank()
}
