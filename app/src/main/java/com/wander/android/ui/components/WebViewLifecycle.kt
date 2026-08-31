package com.wander.android.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Stops a [WebView] costing battery once nobody is looking at it.
 *
 * A WebView left alone keeps its JavaScript timers, its network requests and any media it started
 * running after the screen it lives on has gone. Compose detaches the view when the composable
 * leaves, which stops it drawing and nothing else — so without this, browsing an import page and
 * pressing home leaves timers firing for as long as the process survives.
 *
 * [WebView.pauseTimers] is process-wide rather than per-instance: it suspends the shared JS
 * timer thread for every WebView in the app. That is correct here only because these screens are
 * full-screen and never coexist. A second, simultaneously-visible WebView would need
 * [WebView.onPause] alone, which is per-instance.
 */
@Composable
internal fun WebViewLifecycle(webView: WebView?) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, webView) {
        if (webView == null) return@DisposableEffect onDispose { }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    webView.pauseTimers()
                }

                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.resumeTimers()
                }

                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            webView.release()
        }
    }
}

/**
 * Tears a [WebView] down for good.
 *
 * The order matters: [WebView.destroy] on a view still attached to a window throws, and destroying
 * one still loading leaves the request in flight, so the page is stopped and blanked first.
 */
internal fun WebView.release() {
    stopLoading()
    onPause()
    pauseTimers()
    loadUrl("about:blank")
    clearHistory()
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}
