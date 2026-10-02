package com.wander.android.ui.components

import android.content.Context
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Answers `WebChromeClient.onCreateWindow` by handing the request a genuine second [WebView]
 * rather than the one that asked for it.
 *
 * Some "Continue with Google" buttons (Deezer's among them) use Google Identity Services' popup
 * mode: the button calls `window.open()`, and the page that opens posts the credential back to
 * `window.opener` with `postMessage` and then calls `window.close()` on itself once done. Two
 * simpler-looking approaches both fail this specific handshake:
 * - Handing the transport back the same WebView that asked for it crashes outright —
 *   `IllegalArgumentException: Parent WebView cannot host its own popup window`.
 * - Leaving multi-window support off, so `window.open()` just navigates the same WebView, drops
 *   the handshake instead: there is no separate `opener` left for the "popup" to `postMessage`
 *   into, and nothing to `close()` back out of, so the flow dead-ends on Google's confirmation
 *   page — the white/blank page this works around.
 */
internal fun webChromeClientHostingPopups(
    context: Context,
    onPopupCreated: (WebView) -> Unit,
    onPopupClosed: () -> Unit
): WebChromeClient = object : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            stripWebViewUserAgentToken()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return launchNonWebUrl(context, url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) = onPopupClosed()
            }
        }
        onPopupCreated(popup)
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}

/**
 * Shows [popup] full-screen for as long as it's non-null, then gets out of the way. The popup is
 * expected to close itself once its sign-in flow finishes; [onDismissRequest] only covers the
 * person backing out of it manually (system back gesture, tap outside).
 *
 * This owns the popup's teardown: it is released whenever it stops being shown — closed by the
 * page, dismissed, or the whole screen leaving while it was still open — so callers only ever
 * clear their reference to it.
 */
@Composable
internal fun WebViewPopupDialog(popup: WebView?, onDismissRequest: () -> Unit) {
    if (popup == null) return
    DisposableEffect(popup) { onDispose { popup.release() } }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        key(popup) {
            AndroidView(factory = { popup }, modifier = Modifier.fillMaxSize())
        }
    }
}
