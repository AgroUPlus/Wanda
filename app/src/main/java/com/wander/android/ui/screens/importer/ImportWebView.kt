package com.wander.android.ui.screens.importer

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wander.android.data.importer.IMPORT_WEB_USER_AGENT

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImportWebView(
    webUrl: String,
    reloadToken: Int,
    detectedUrl: String?,
    isLoadingPlaylist: Boolean,
    isDiscovering: Boolean,
    pageError: String?,
    onUrlChanged: (String) -> Unit,
    onCookieCaptured: (String?) -> Unit,
    onPageError: (String) -> Unit,
    onClearPageError: () -> Unit,
    onLoadPlaylist: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageProgress by remember { mutableIntStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastLoad by remember { mutableStateOf(webUrl to reloadToken) }

    val reload: () -> Unit = {
        onClearPageError()
        webViewInstance?.reload()
        Unit
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Sign In & Browse",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = reload) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Logout / Switch Account",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (pageProgress in 1..99) {
            LinearWavyProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        webViewInstance = this
                        // Opaque: a transparent WebView that fails to paint is indistinguishable
                        // from the app surface behind it, which reads as a blank grey page.
                        setBackgroundColor(Color.WHITE)

                        settings.userAgentString = IMPORT_WEB_USER_AGENT
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        val shimAtDocumentStart = installStorageAccessShim(this)

                        webChromeClient = importWebChromeClient { pageProgress = it }
                        webViewClient = importWebViewClient(
                            fallbackUrl = webUrl,
                            callbacks = ImportWebViewCallbacks(
                                onUrlChanged = onUrlChanged,
                                onCookieCaptured = onCookieCaptured,
                                onPageError = onPageError,
                                onVisit = { url ->
                                    if (!shimAtDocumentStart) injectStorageAccessShim(this)
                                    onUrlChanged(url)
                                    onCookieCaptured(CookieManager.getInstance().getCookie(url))
                                }
                            )
                        )
                        loadUrl(webUrl)
                    }
                },
                update = { webView ->
                    val target = webUrl to reloadToken
                    if (lastLoad != target) {
                        lastLoad = target
                        webView.loadUrl(webUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isDiscovering) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Detecting your playlists...", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (pageError != null) {
                ImportWebViewError(message = pageError, onRetry = reload, modifier = Modifier.fillMaxSize())
            }

            // Floating bar when playlist detected on web page
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedUrl != null || isLoadingPlaylist,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (isLoadingPlaylist) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Loading playlist...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Playlist detected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { detectedUrl?.let { onLoadPlaylist(it) } },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text("Import")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportWebViewError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text("Retry")
            }
        }
    }
}
