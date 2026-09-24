package com.wander.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The only place credentials live. Backed by the Android Keystore via
 * [EncryptedSharedPreferences] — nothing here is ever written to Room, to logs, or to backups.
 */
class SecureStorage internal constructor(private val prefs: SharedPreferences) {

    private val displayPrefs = SecureDisplayPreferences(prefs)
    private val playbackPrefs = SecurePlaybackPreferences(prefs)
    private val accountPrefs = SecureAccountPreferences(prefs)
    private val agroPrefs = SecureAgroPreferences(prefs)
    private val appPrefs = SecureAppPreferences(prefs)

    // ── Display Preferences ─────────────────────────────────────────────────────────────────
    val isAmoledBlack: StateFlow<Boolean> = displayPrefs.isAmoledBlack
    fun setAmoledBlack(enabled: Boolean) = displayPrefs.setAmoledBlack(enabled)

    val isBackBlurEnabled: StateFlow<Boolean> = displayPrefs.isBackBlurEnabled
    fun setBackBlurEnabled(enabled: Boolean) = displayPrefs.setBackBlurEnabled(enabled)

    val isMonetDynamic: StateFlow<Boolean> = displayPrefs.isMonetDynamic
    fun setMonetDynamic(enabled: Boolean) = displayPrefs.setMonetDynamic(enabled)

    val isImmersivePlayer: StateFlow<Boolean> = displayPrefs.isImmersivePlayer
    fun setImmersivePlayer(enabled: Boolean) = displayPrefs.setImmersivePlayer(enabled)

    val isCoverArtThemeEnabled: StateFlow<Boolean> = displayPrefs.isCoverArtThemeEnabled
    fun setCoverArtThemeEnabled(enabled: Boolean) = displayPrefs.setCoverArtThemeEnabled(enabled)

    val isReduceMotion: StateFlow<Boolean> = displayPrefs.isReduceMotion
    fun setReduceMotion(enabled: Boolean) = displayPrefs.setReduceMotion(enabled)

    val isLetterByLetterLyricsEnabled: StateFlow<Boolean> = displayPrefs.isLetterByLetterLyricsEnabled
    fun setLetterByLetterLyricsEnabled(enabled: Boolean) = displayPrefs.setLetterByLetterLyricsEnabled(enabled)

    val isCoverCarouselEnabled: StateFlow<Boolean> = displayPrefs.isCoverCarouselEnabled
    fun setCoverCarouselEnabled(enabled: Boolean) = displayPrefs.setCoverCarouselEnabled(enabled)

    // ── Playback Preferences ────────────────────────────────────────────────────────────────
    val isOfflineMode: StateFlow<Boolean> = playbackPrefs.isOfflineMode
    fun setOfflineMode(enabled: Boolean) = playbackPrefs.setOfflineMode(enabled)

    val isPreloadNextEnabled: StateFlow<Boolean> = playbackPrefs.isPreloadNextEnabled
    fun setPreloadNextEnabled(enabled: Boolean) = playbackPrefs.setPreloadNextEnabled(enabled)

    val isSkipSilenceEnabled: StateFlow<Boolean> = playbackPrefs.isSkipSilenceEnabled
    fun setSkipSilenceEnabled(enabled: Boolean) = playbackPrefs.setSkipSilenceEnabled(enabled)

    val isIndexOnMobileDataEnabled: StateFlow<Boolean> = playbackPrefs.isIndexOnMobileDataEnabled
    fun setIndexOnMobileDataEnabled(enabled: Boolean) = playbackPrefs.setIndexOnMobileDataEnabled(enabled)

    val isRadioMode: StateFlow<Boolean> = playbackPrefs.isRadioMode
    fun setRadioMode(enabled: Boolean) = playbackPrefs.setRadioMode(enabled)

    val isExternalLyricsEnabled: StateFlow<Boolean> = playbackPrefs.isExternalLyricsEnabled
    fun setExternalLyricsEnabled(enabled: Boolean) = playbackPrefs.setExternalLyricsEnabled(enabled)

    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = playbackPrefs.isAutoUpdateCheckEnabled
    fun setAutoUpdateCheckEnabled(enabled: Boolean) = playbackPrefs.setAutoUpdateCheckEnabled(enabled)

    val isArtistReleaseNotificationEnabled: StateFlow<Boolean> = playbackPrefs.isArtistReleaseNotificationEnabled
    fun setArtistReleaseNotificationEnabled(enabled: Boolean) = playbackPrefs.setArtistReleaseNotificationEnabled(enabled)

    var preferredAudioLanguage: String?
        get() = playbackPrefs.preferredAudioLanguage
        set(value) { playbackPrefs.preferredAudioLanguage = value }

    // ── Account Credentials (Navidrome & YouTube Music) ──────────────────────────────────────
    val navidromeConfigured: StateFlow<Boolean> = accountPrefs.navidromeConfigured
    val navidromeServerUrl: String get() = accountPrefs.navidromeServerUrl
    val navidromeUsername: String get() = accountPrefs.navidromeUsername
    val navidromePassword: String get() = accountPrefs.navidromePassword
    fun setNavidromeCredentials(u: String, user: String, pass: String) = accountPrefs.setNavidromeCredentials(u, user, pass)
    fun clearNavidromeCredentials() = accountPrefs.clearNavidromeCredentials()

    val ytMusicConfigured: StateFlow<Boolean> = accountPrefs.ytMusicConfigured
    val ytMusicAuthCookie: String get() = accountPrefs.ytMusicAuthCookie
    val ytMusicVisitorData: String get() = accountPrefs.ytMusicVisitorData
    var ytMusicAccountName: String
        get() = accountPrefs.ytMusicAccountName
        set(value) { accountPrefs.ytMusicAccountName = value }
    fun setYtMusicSession(cookie: String, visitor: String = ytMusicVisitorData) = accountPrefs.setYtMusicSession(cookie, visitor)
    fun clearYtMusicSession() = accountPrefs.clearYtMusicSession()

    // ── Agro Server Integration ─────────────────────────────────────────────────────────────
    val agroConfigured: StateFlow<Boolean> = agroPrefs.agroConfigured
    val agroServerUrl: String get() = agroPrefs.agroServerUrl
    val agroApiKey: String get() = agroPrefs.agroApiKey
    val agroUsername: String get() = agroPrefs.agroUsername
    val agroDevicePetname: String get() = agroPrefs.agroDevicePetname
    fun setAgroDevicePetname(petname: String) = agroPrefs.setAgroDevicePetname(petname)

    var catalogCursor: Long
        get() = agroPrefs.catalogCursor
        set(value) { agroPrefs.catalogCursor = value }

    var catalogLastPublishedAt: Long
        get() = agroPrefs.catalogLastPublishedAt
        set(value) { agroPrefs.catalogLastPublishedAt = value }

    var agroCapabilities: Set<String>
        get() = agroPrefs.agroCapabilities
        set(value) { agroPrefs.agroCapabilities = value }

    fun serverSupports(capability: String): Boolean = agroPrefs.serverSupports(capability)
    val agroDeviceId: String get() = agroPrefs.agroDeviceId
    fun setAgroCredentials(u: String, user: String, key: String, devId: String = agroDeviceId) =
        agroPrefs.setAgroCredentials(u, user, key, devId)

    val agroP2pSyncFlow: StateFlow<Boolean> = agroPrefs.agroP2pSyncFlow
    val agroP2pSync: Boolean get() = agroPrefs.agroP2pSync
    fun setAgroP2pSync(enabled: Boolean) = agroPrefs.setAgroP2pSync(enabled)

    val agroPopularityContributionFlow: StateFlow<Boolean> = agroPrefs.agroPopularityContributionFlow
    val agroPopularityContribution: Boolean get() = agroPrefs.agroPopularityContribution
    fun setAgroPopularityContribution(enabled: Boolean) = agroPrefs.setAgroPopularityContribution(enabled)

    val agroCatalogTradeFlow: StateFlow<Boolean> = agroPrefs.agroCatalogTradeFlow
    val agroCatalogTrade: Boolean get() = agroPrefs.agroCatalogTrade
    fun setAgroCatalogTrade(enabled: Boolean) = agroPrefs.setAgroCatalogTrade(enabled)

    val agroServerArchiveFlow: StateFlow<Boolean> = agroPrefs.agroServerArchiveFlow
    val agroServerArchive: Boolean get() = agroPrefs.agroServerArchive
    fun setAgroServerArchive(enabled: Boolean) = agroPrefs.setAgroServerArchive(enabled)

    val agroLibrarySyncFlow: StateFlow<Boolean> = agroPrefs.agroLibrarySyncFlow
    val agroLibrarySync: Boolean get() = agroPrefs.agroLibrarySync
    fun setAgroLibrarySync(enabled: Boolean) = agroPrefs.setAgroLibrarySync(enabled)

    val agroProxyEnabled: StateFlow<Boolean> = agroPrefs.agroProxyEnabled
    fun setAgroProxyEnabled(enabled: Boolean) = agroPrefs.setAgroProxyEnabled(enabled)

    var agroVaultKey: ByteArray?
        get() = agroPrefs.agroVaultKey
        set(value) { agroPrefs.agroVaultKey = value }

    val agroSyncSettings: StateFlow<Boolean> = agroPrefs.agroSyncSettings
    fun setAgroSyncSettings(enabled: Boolean) = agroPrefs.setAgroSyncSettings(enabled)

    var agroIdentityPrivateKey: String?
        get() = agroPrefs.agroIdentityPrivateKey
        set(value) { agroPrefs.agroIdentityPrivateKey = value }

    var agroIdentityPublicKey: String?
        get() = agroPrefs.agroIdentityPublicKey
        set(value) { agroPrefs.agroIdentityPublicKey = value }

    fun clearAgroCredentials() = agroPrefs.clearAgroCredentials()

    // ── App State, Scanning, Watermarks & Jobs ───────────────────────────────────────────────
    var lastSeenReleaseWatermark: Long
        get() = appPrefs.lastSeenReleaseWatermark
        set(value) { appPrefs.lastSeenReleaseWatermark = value }

    var duplicateScanCursor: String
        get() = appPrefs.duplicateScanCursor
        set(value) { appPrefs.duplicateScanCursor = value }

    var lastNotifiedRelease: String
        get() = appPrefs.lastNotifiedRelease
        set(value) { appPrefs.lastNotifiedRelease = value }

    var lastSeenReplayYear: Int
        get() = appPrefs.lastSeenReplayYear
        set(value) { appPrefs.lastSeenReplayYear = value }

    var isIncognitoMode: Boolean
        get() = appPrefs.isIncognitoMode
        set(value) { appPrefs.isIncognitoMode = value }

    val hasCompletedSetup: StateFlow<Boolean> = appPrefs.hasCompletedSetup
    fun markSetupComplete() = appPrefs.markSetupComplete()

    var localScanWatermark: Long
        get() = appPrefs.localScanWatermark
        set(value) { appPrefs.localScanWatermark = value }

    var pendingForget: Set<String>
        get() = appPrefs.pendingForget
        set(value) { appPrefs.pendingForget = value }

    var localScanFolder: String?
        get() = appPrefs.localScanFolder
        set(value) { appPrefs.localScanFolder = value }

    var localScanFolderLabel: String?
        get() = appPrefs.localScanFolderLabel
        set(value) { appPrefs.localScanFolderLabel = value }

    fun workPaused(kindName: String): StateFlow<Boolean> = appPrefs.workPaused(kindName)
    fun setWorkPaused(kindName: String, paused: Boolean) = appPrefs.setWorkPaused(kindName, paused)

    // ── Sharing ─────────────────────────────────────────────────────────────────────────────
    val shareDomain: StateFlow<String> = appPrefs.shareDomain
    val agroShareDomain: StateFlow<String> = appPrefs.agroShareDomain
    val agroShareHosts: String get() = appPrefs.agroShareHosts
    fun setAgroShareSettings(domain: String, hosts: String) = appPrefs.setAgroShareSettings(domain, hosts)
    fun setShareDomain(domain: String) = appPrefs.setShareDomain(domain)

    private val backupManager = SecureStorageBackup(prefs)

    // ── Backup & Wipe ───────────────────────────────────────────────────────────────────────
    fun exportAll(): Map<String, Any?> = backupManager.exportAll()
    fun isAccountKey(key: String): Boolean = backupManager.isAccountKey(key)
    fun importAll(values: Map<String, Any>, replaces: (String) -> Boolean) =
        backupManager.importAll(values, replaces)

    fun clearAllCredentials() {
        val deviceId = prefs.getString(KEY_AGRO_DEVICE_ID, null)
        prefs.edit {
            clear()
            deviceId?.let { putString(KEY_AGRO_DEVICE_ID, it) }
        }
        displayPrefs.resetFlows()
        playbackPrefs.resetFlows()
        accountPrefs.resetFlows()
        agroPrefs.resetFlows()
        appPrefs.resetFlows()
    }


    companion object {
        const val KEY_CATALOG_CURSOR = com.wander.android.core.security.KEY_CATALOG_CURSOR
        const val KEY_CATALOG_PUBLISHED_AT = com.wander.android.core.security.KEY_CATALOG_PUBLISHED_AT

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
