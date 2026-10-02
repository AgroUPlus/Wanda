package com.wander.android.core.security

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Clears what the playlist importer's embedded browser left in Android's shared WebView store.
 *
 * Importing from Spotify and Apple Music used to run through a WebView the person signed in to,
 * leaving those sessions in [CookieManager] and [WebStorage] with nothing in the app able to sign
 * them out. The importer no longer has a browser, so nothing is left to use them. Runs once.
 *
 * Wiping the whole store is safe for the accounts this app does use: the YouTube Music and Deezer
 * sessions are captured out of their login WebViews into [SecureStorage] and read from there, so
 * nobody is signed out of them.
 */
internal object LegacyWebViewPurge {
    private const val PREFS = "migrations"
    private const val KEY_DONE = "legacy_importer_webview_purged"

    fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return

        CookieManager.getInstance().apply {
            // The flag is set once the cookies are actually gone, so a process killed mid-purge
            // simply tries again on the next start.
            removeAllCookies { prefs.edit().putBoolean(KEY_DONE, true).apply() }
            flush()
        }
        WebStorage.getInstance().deleteAllData()
    }
}
