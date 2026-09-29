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
import com.wander.android.ui.components.WebViewLifecycle
import com.wander.android.ui.components.WebViewPopupDialog
import com.wander.android.ui.components.blockLoginAsset
import com.wander.android.ui.components.launchNonWebUrl
import com.wander.android.ui.components.prepareForLogin
import com.wander.android.ui.components.release
import com.wander.android.ui.components.stripWebViewUserAgentToken
import com.wander.android.ui.components.webChromeClientHostingPopups
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private const val DEEZER_LOGIN_URL = "https://www.deezer.com/en/login"
private const val DEEZER_DOMAIN_COOKIE = "https://www.deezer.com"

/** How often to re-check for the session cookie while nothing has captured it yet. */
private const val COOKIE_POLL_INTERVAL_MS = 500L

/**
 * Signs in to Deezer in an embedded WebView and intercepts the resulting `arl` session cookie.
 * Also provides a manual ARL token input field.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
    onDone: () -> Unit,
    viewModel: DeezerLoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    var loading by remember { mutableStateOf(true) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    WebViewLifecycle(webViewInstance)

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onDone()
    }

    // `onPageFinished` alone missed this: deezer.com sets `arl` via a client-side script after its
    // own post-login redirect finishes rendering, rather than as a `Set-Cookie` header on the
    // redirect itself, and once signed in its account page is a single-page app that navigates
    // internally with no further full page load to re-trigger the check at all. From here that
    // read as a real, permanent bug — the WebView sat on Deezer's own (blank-looking) post-login
    // page forever, since nothing ever looked at the cookie jar again. Polling it directly, on a
    // timer, is independent of whichever way any given login page happens to hand the cookie over.
    LaunchedEffect(state.isSignedIn) {
        while (!state.isSignedIn) {
            delay(COOKIE_POLL_INTERVAL_MS)
            val cookie = CookieManager.getInstance().getCookie(DEEZER_DOMAIN_COOKIE)
            if (cookie != null && cookie.contains("arl=")) {
                viewModel.onSessionCaptured(cookie)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Text(
            text = stringResource(R.string.login_sign_deezer),
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
                        // Deezer's own login screen offers "Continue with Google" — without this,
                        // tapping it landed on Google's blank "this browser may not be secure" refusal
                        // page instead of the actual sign-in flow.
                        stripWebViewUserAgentToken()
                        // "Continue with Google" needs a genuine second WebView — see
                        // `webChromeClientHostingPopups`'s own doc for why neither reusing this WebView
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
                        val captured = AtomicBoolean(false)
                        webViewClient = object : WebViewClient() {
                            override fun onPageCommitVisible(view: WebView, url: String) {
                                loading = false
                            }

                            // The 500 ms poll above catches `arl` when a script sets it; this catches it
                            // the moment a response header does, without waiting for a page to finish.
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val cookie = CookieManager.getInstance().getCookie(DEEZER_DOMAIN_COOKIE)
                                if (cookie != null && cookie.contains("arl=") && captured.compareAndSet(false, true)) {
                                    view.post { viewModel.onSessionCaptured(cookie) }
                                }
                                return blockLoginAsset(request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val cookie = CookieManager.getInstance().getCookie(DEEZER_DOMAIN_COOKIE)
                                if (cookie != null && cookie.contains("arl=")) {
                                    viewModel.onSessionCaptured(cookie)
                                }
                            }

                            // Google's own sign-in step can redirect through an `intent://` or
                            // `market://` URI. Left un-overridden this throws uncaught the moment it
                            // hits one Android cannot resolve inside the WebView itself — see
                            // `launchNonWebUrl`'s own doc — which is what crashed the app on exactly
                            // this login, not anything specific to Deezer's own page.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return launchNonWebUrl(context, url)
                            }
                        }
                        loadUrl(DEEZER_LOGIN_URL)
                    }
                },
                onRelease = { webView ->
                    webViewInstance = null
                    webView.release()
                },
                modifier = Modifier.fillMaxSize()
            )
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(56.dp).align(Alignment.Center))
            }
        }

        HorizontalDivider()

        OutlinedTextField(
            value = state.manualArl,
            onValueChange = viewModel::onManualArlChange,
            label = { Text(stringResource(R.string.login_paste_arl)) },
            isError = state.error != null,
            supportingText = state.error?.let { msg -> { Text(msg) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(
                onClick = viewModel::submitManualArl,
                enabled = state.manualArl.isNotBlank() && !state.isLoading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.login_use_pasted_arl))
            }
            // Missing before: a login that never completes — the WebView stuck on Deezer's own
            // blank-looking post-login page, say — left no way back out but the system gesture.
            TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) }
        }
    }

    WebViewPopupDialog(popup = popupWebView) {
        popupWebView?.release()
        popupWebView = null
    }
}
