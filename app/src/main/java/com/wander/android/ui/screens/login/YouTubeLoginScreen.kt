package com.wander.android.ui.screens.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import com.wander.android.ui.components.WebViewLifecycle
import com.wander.android.ui.components.blockLoginAsset
import com.wander.android.ui.components.launchNonWebUrl
import com.wander.android.ui.components.prepareForLogin
import com.wander.android.ui.components.stripWebViewUserAgentToken
import java.util.concurrent.atomic.AtomicBoolean

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

    var loading by remember { mutableStateOf(true) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
            text = stringResource(R.string.login_sign_youtube_music),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        webViewInstance = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        prepareForLogin(darkTheme)
                        // This screen's sign-in *is* Google's own — without this, Google's server
                        // identifies the request as coming from inside a WebView and refuses it with a
                        // blank "this browser may not be secure" page instead of the real consent flow.
                        stripWebViewUserAgentToken()
                        val captured = AtomicBoolean(false)
                        webViewClient = object : WebViewClient() {
                            // Runs on a WebView-internal thread for every request the page makes, which
                            // catches the session cookie the moment a response sets it — seconds before
                            // the heavy web player finishes loading and `onPageFinished` fires.
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val cookie = CookieManager.getInstance().getCookie(YT_MUSIC_URL)
                                if (cookie != null && GoogleAccountManager.isUsable(cookie) &&
                                    captured.compareAndSet(false, true)
                                ) {
                                    view.post { captureSession(view, cookie, viewModel) }
                                }
                                return blockLoginAsset(request)
                            }

                            override fun onPageCommitVisible(view: WebView, url: String) {
                                loading = false
                            }

                            // Google's sign-in can redirect through an `intent://` or `market://`
                            // URI, which WebView cannot load and throws on uncaught — see
                            // `launchNonWebUrl`.
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = launchNonWebUrl(context, request.url.toString())
                        }
                        loadUrl(YT_MUSIC_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(56.dp).align(Alignment.Center))
            }
        }

        HorizontalDivider()

        OutlinedTextField(
            value = state.manualCookie,
            onValueChange = viewModel::onManualCookieChange,
            label = { Text(stringResource(R.string.login_paste_cookie_header)) },
            singleLine = false,
            maxLines = 3,
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(stringResource(error)) } },
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
                Text(stringResource(R.string.login_use_pasted_cookie))
            }
            TextButton(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

/**
 * visitorData identifies the session to InnerTube. Without it requests look like they come from
 * nowhere and get challenged more aggressively. The page keeps it in its own config object, which
 * may not exist yet this early — an empty value is accepted and resolved later.
 */
private fun captureSession(view: WebView, cookie: String, viewModel: YouTubeLoginViewModel) {
    view.evaluateJavascript(VISITOR_DATA_SCRIPT) { raw ->
        viewModel.onSessionCaptured(cookie, unquote(raw))
    }
}
