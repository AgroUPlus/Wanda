package com.wander.android.ui.screens.importer

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wander.android.data.importer.IMPORT_WEB_USER_AGENT
import com.wander.android.ui.components.WebViewLifecycle
import com.wander.android.ui.components.WebViewPopupDialog
import com.wander.android.ui.components.launchNonWebUrl
import com.wander.android.ui.components.release
import com.wander.android.ui.components.webChromeClientHostingPopups

/**
 * Embedded WebView for external platforms (Spotify, Deezer, Apple Music).
 *
 * Configured to capture and synchronize cookies in [CookieManager] so web-authenticated
 * requests succeed without requiring developer API keys.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ExternalPlatformWebView(
    webUrl: String,
    onUrlChanged: (String) -> Unit = {},
    onCookieCaptured: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    WebViewLifecycle(webViewInstance)

    AndroidView(
        factory = { context ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(context).apply {
                webViewInstance = this
                setBackgroundColor(Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = IMPORT_WEB_USER_AGENT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // Spotify's/Deezer's own "Continue with Google" step needs a genuine second WebView
                // — see `webChromeClientHostingPopups`'s own doc for why neither reusing this WebView
                // nor disabling popups outright works for this specific sign-in flow.
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                webChromeClient = webChromeClientHostingPopups(
                    context = context,
                    onPopupCreated = { popupWebView = it },
                    onPopupClosed = {
                        popupWebView?.release()
                        popupWebView = null
                    }
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val effectiveUrl = url ?: webUrl
                        val cookie = CookieManager.getInstance().getCookie(effectiveUrl)
                        CookieManager.getInstance().flush()
                        if (!cookie.isNullOrBlank()) {
                            onCookieCaptured(cookie)
                        }
                        onUrlChanged(effectiveUrl)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        url?.let { onUrlChanged(it) }
                    }

                    // A login step embedded here — Spotify's or Deezer's own "Continue with
                    // Google" — can redirect through an `intent://` or `market://` URI at any
                    // point. Left un-overridden, the WebView hands that straight to Android and an
                    // unresolvable one throws uncaught, from inside WebView's own code rather than
                    // this composable's — see `launchNonWebUrl`'s own doc.
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return launchNonWebUrl(context, url)
                    }
                }
                loadUrl(webUrl)
            }
        },
        onRelease = {
            webViewInstance = null
        },
        modifier = modifier.fillMaxSize()
    )

    WebViewPopupDialog(popup = popupWebView) {
        popupWebView?.release()
        popupWebView = null
    }
}
