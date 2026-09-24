package com.wander.android.ui.screens.importer

import android.annotation.SuppressLint
import android.graphics.Color
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
 * Spotify's web player, shown so the user can visually browse and find a playlist — and, just as
 * importantly, the live browser context [SpotifyWebFetch.fetchText] needs to make Spotify's own API
 * accept a request at all. `open.spotify.com` (unlike its login page) already renders correctly
 * here; nothing beyond a plain WebView is needed for it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyBrowseWebView(
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    WebViewLifecycle(webViewInstance)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewInstance = this
                setBackgroundColor(Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = IMPORT_WEB_USER_AGENT
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onWebViewReady(this@apply)
                    }
                }
                loadUrl("https://open.spotify.com/")
            }
        },
        // Single teardown owner — see ImportWebViewClients' history: WebViewLifecycle's own
        // disposal already calls `release()` on this same instance once `webViewInstance` goes
        // null, and `release()` is not safe to call twice.
        onRelease = { webViewInstance = null },
        modifier = modifier.fillMaxSize()
    )
}
