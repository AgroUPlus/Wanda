package com.wander.android.ui.screens.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import com.wander.android.ui.components.WebViewLifecycle
import com.wander.android.ui.components.release

private const val YT_MUSIC_URL = "https://music.youtube.com"

private const val VISITOR_DATA_SCRIPT =
    "(function(){try{return window.ytcfg.get('VISITOR_DATA')||''}catch(e){return ''}})()"

/** `evaluateJavascript` hands back a JSON literal, so a plain string arrives quoted. */
private fun unquote(raw: String?): String =
    raw.orEmpty().removeSurrounding("\"").takeIf { it != "null" }.orEmpty()

/**
 * Signs in to YouTube Music in an embedded WebView and reads the resulting cookie straight from
 * the platform cookie store — the credential never passes through a third party, and no Google
 * API key is required. The manual field below is the fallback for anyone who prefers to paste a
 * cookie exported from their desktop browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onDone: () -> Unit,
    viewModel: YouTubeLoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    WebViewLifecycle(webViewInstance)

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onDone()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Text(
            text = "Sign in to YouTube Music",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        AndroidView(
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    webViewInstance = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookie = CookieManager.getInstance().getCookie(YT_MUSIC_URL)
                            if (cookie == null || !GoogleAccountManager.isUsable(cookie)) return

                            // visitorData identifies the session to InnerTube. Without it
                            // requests look like they come from nowhere and get challenged more
                            // aggressively. The page keeps it in its own config object.
                            view?.evaluateJavascript(VISITOR_DATA_SCRIPT) { raw ->
                                viewModel.onSessionCaptured(cookie, unquote(raw))
                            } ?: viewModel.onSessionCaptured(cookie, "")
                        }
                    }
                    loadUrl(YT_MUSIC_URL)
                }
            },
            onRelease = { webView ->
                webViewInstance = null
                webView.release()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        HorizontalDivider()

        OutlinedTextField(
            value = state.manualCookie,
            onValueChange = viewModel::onManualCookieChange,
            label = { Text("Or paste a cookie header") },
            singleLine = false,
            maxLines = 3,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            TextButton(
                onClick = viewModel::submitManualCookie,
                enabled = state.manualCookie.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Use pasted cookie")
            }
            TextButton(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        }
    }
}
