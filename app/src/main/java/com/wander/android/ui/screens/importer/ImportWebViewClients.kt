package com.wander.android.ui.screens.importer

import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wander.android.BuildConfig

private const val TAG = "ImportWebView"

/**
 * Callbacks the sign-in WebView hands back to the screen.
 *
 * [onPageError] exists because a page that fails to render is otherwise indistinguishable from one
 * that is still loading: the WebView simply paints nothing.
 */
internal class ImportWebViewCallbacks(
    val onUrlChanged: (String) -> Unit,
    val onCookieCaptured: (String?) -> Unit,
    val onPageError: (String) -> Unit,
    val onVisit: (String) -> Unit
)

internal fun importWebViewClient(
    fallbackUrl: String,
    callbacks: ImportWebViewCallbacks
): WebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        callbacks.onVisit(url ?: fallbackUrl)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        callbacks.onVisit(url ?: fallbackUrl)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        callbacks.onVisit(url ?: fallbackUrl)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame != true) return
        val host = request.url?.host ?: "the page"
        val code = error?.errorCode ?: 0
        val description = error?.description ?: "unknown error"
        callbacks.onPageError("Could not load $host ($description, code $code).")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (request?.isForMainFrame != true) return
        val host = request.url?.host ?: "the page"
        val status = errorResponse?.statusCode ?: 0
        callbacks.onPageError("$host refused the request (HTTP $status).")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // Never proceed(): a sign-in page is exactly where a bad certificate matters most.
        handler?.cancel()
        callbacks.onPageError("The connection is not secure (SSL error ${error?.primaryError}).")
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        callbacks.onPageError("The browser stopped unexpectedly. Tap retry to load it again.")
        // Returning true keeps the app alive; the dead WebView is replaced on retry.
        return true
    }
}

internal fun importWebChromeClient(
    onProgress: (Int) -> Unit
): WebChromeClient = object : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    /**
     * Federated sign-in ("Continue with Google/Apple/Facebook") arrives as `window.open`.
     * Route it back into the same WebView instead of dropping it.
     */
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

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (BuildConfig.DEBUG && consoleMessage != null) {
            // Message text only — never the source URL, which can carry session parameters.
            Log.d(TAG, "${consoleMessage.messageLevel()}: ${consoleMessage.message()}")
        }
        return true
    }
}
