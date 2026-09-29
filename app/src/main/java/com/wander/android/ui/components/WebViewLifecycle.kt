package com.wander.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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

/**
 * Trims the default `wv`/version token Android appends to every [WebView]'s user agent, leaving an
 * otherwise ordinary mobile Chrome string.
 *
 * Google's own sign-in refuses to run at all inside a WebView it can identify as one — the login
 * itself completes past that check, but Deezer and YouTube Music both offer a "Continue with
 * Google" step inside their own embedded login screens here, and hitting it landed on Google's
 * blank refusal page rather than a working consent screen. The token this strips is exactly what
 * that check keys on; nothing else about the request changes.
 */
internal fun WebView.stripWebViewUserAgentToken() {
    settings.userAgentString = settings.userAgentString
        .replace(Regex("""\s*wv"""), "")
        .replace(Regex("""Version/[\d.]+\s*"""), "")
}

/**
 * Hands a non-`http(s)` URL a [WebView] is about to navigate to off to a real app instead, and
 * reports whether it did — the return value a `shouldOverrideUrlLoading` override needs to know
 * whether to let the WebView load the URL itself or not.
 *
 * A login flow embedded in a WebView — Google's own sign-in inside Deezer's or YouTube Music's
 * login page here — can redirect through an `intent://` URI, a `market://` link, or another app's
 * own scheme (an authenticator, a passkey provider) at any step. A [WebView] handed one of those
 * with no override throws [ActivityNotFoundException] *uncaught*, from inside WebView's own
 * internals rather than this app's call stack — which is what crashed the app on exactly this kind
 * of redirect, not anything the login screen's own code was doing wrong.
 */
internal fun launchNonWebUrl(context: Context, url: String): Boolean {
    if (url.startsWith("http://") || url.startsWith("https://")) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // No app on the device can open it — a `market://` link with no Play Store, an
        // authenticator scheme nothing here has installed. The WebView simply does not navigate;
        // that reads as the tap doing nothing, which is a far smaller failure than crashing.
        Log.w("WebViewLifecycle", "No app to handle $url")
        true
    } catch (e: Exception) {
        // Malformed `intent://` URIs and similar can throw before `startActivity` is even reached.
        // Same reasoning as above: swallow it, the WebView was never going to load this itself.
        Log.w("WebViewLifecycle", "Could not open $url", e)
        true
    }
}
