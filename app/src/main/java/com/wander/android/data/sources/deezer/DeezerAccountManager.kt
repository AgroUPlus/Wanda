package com.wander.android.data.sources.deezer

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Deezer user session.
 *
 * Holds the user's `arl` session cookie securely in [SecureStorage].
 * On sign-out, completely purges [CookieManager] and [WebStorage] in accordance
 * with Wanda's security invariants.
 */
@Singleton
class DeezerAccountManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    val isLoggedIn: StateFlow<Boolean> = secureStorage.deezerConfigured

    val arl: String get() = secureStorage.deezerAuthArl

    val accountName: String get() = secureStorage.deezerAccountName

    val tier: DeezerAccountTier
        get() = DeezerAccountTier.fromString(secureStorage.deezerAccountTier)

    val audioQuality: String
        get() = secureStorage.deezerAudioQuality

    fun setSession(arl: String, name: String = "", tier: DeezerAccountTier = DeezerAccountTier.FREE) {
        if (arl.isBlank()) return
        secureStorage.setDeezerSession(arl.trim(), name.trim(), tier.name)
    }

    fun updateTier(tier: DeezerAccountTier) {
        secureStorage.deezerAccountTier = tier.name
    }

    fun updateAccountName(name: String) {
        secureStorage.deezerAccountName = name.trim()
    }

    /**
     * Purges credentials and all browser-side storage.
     *
     * The in-app sign-in runs via an embedded WebView. Left alone, deezer.com would retain
     * authentication cookies in Android's shared CookieManager even after clearing app preferences.
     */
    fun signOut() {
        secureStorage.clearDeezerSession()
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
    }
}
