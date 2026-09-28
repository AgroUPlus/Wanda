package com.wander.android.ui.screens.importer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import com.wander.android.ui.components.release

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
    onWebViewReady: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit = {},
    onCookieCaptured: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
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
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = IMPORT_WEB_USER_AGENT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val host = view ?: return false
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        transport.webView = host
                        resultMsg.sendToTarget()
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val effectiveUrl = url ?: webUrl
                        val cookie = CookieManager.getInstance().getCookie(effectiveUrl)
                        CookieManager.getInstance().flush()
                        if (!cookie.isNullOrBlank()) {
                            onCookieCaptured(cookie)
                        }
                        onUrlChanged(effectiveUrl)
                        onWebViewReady(this@apply)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        url?.let { onUrlChanged(it) }
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
}
