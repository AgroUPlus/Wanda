package com.wander.android.ui.screens.importer

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Spotify's web player calls `document.requestStorageAccess()` during start-up. A WebView always
 * rejects that call — the Storage Access API has no UI to grant it — and the rejection propagates
 * into React as an uncaught error, which tears the tree down and leaves a blank page.
 *
 * The WebView already accepts first- and third-party cookies for these hosts, so storage access is
 * in fact available; only the API that reports it is missing. Resolving the promise tells the page
 * the truth about what it can do.
 */
private const val STORAGE_ACCESS_SHIM = """
(function () {
  try {
    if (typeof document.requestStorageAccess === 'function') {
      document.requestStorageAccess = function () { return Promise.resolve(); };
    }
    if (typeof document.hasStorageAccess === 'function') {
      document.hasStorageAccess = function () { return Promise.resolve(true); };
    }
  } catch (e) {}
})();
"""

private val SHIMMED_ORIGINS = setOf(
    "https://open.spotify.com",
    "https://accounts.spotify.com",
    "https://challenge.spotify.com"
)

/**
 * Installs the shim so it runs before the page's own scripts. Returns false when the WebView
 * implementation is too old to support document-start scripts, in which case
 * [injectStorageAccessShim] is the (later, best-effort) fallback.
 */
internal fun installStorageAccessShim(webView: WebView): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
    WebViewCompat.addDocumentStartJavaScript(webView, STORAGE_ACCESS_SHIM, SHIMMED_ORIGINS)
    return true
}

internal fun injectStorageAccessShim(webView: WebView) {
    webView.evaluateJavascript(STORAGE_ACCESS_SHIM, null)
}
