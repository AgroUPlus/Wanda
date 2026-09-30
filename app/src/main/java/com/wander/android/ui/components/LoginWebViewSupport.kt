package com.wander.android.ui.components

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

private val BLOCKED_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "avif", "ico",
    "woff", "woff2", "ttf", "otf",
    "mp4", "webm", "m4a", "mp3", "ogg"
)

/** Human-verification challenges are made of images, so they must load or the login cannot finish. */
private val CHALLENGE_MARKERS = listOf("captcha", "challenge")

/**
 * Sets up a [WebView] used only to sign in: a transparent background so the first paint shows the
 * app's own surface rather than a white flash, and — where the WebView supports it — algorithmic
 * darkening so the page follows a dark app theme.
 */
internal fun WebView.prepareForLogin(darkTheme: Boolean) {
    setBackgroundColor(Color.TRANSPARENT)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkTheme)
    }
}

/**
 * Answers image, font and media requests with an empty response, or returns `null` to let the
 * request through. A sign-in needs the page's HTML, scripts and auth calls — not artwork or web
 * fonts, which are most of what a music site's landing page weighs.
 */
internal fun blockLoginAsset(request: WebResourceRequest): WebResourceResponse? {
    if (request.isForMainFrame) return null
    val url = request.url
    val path = url.path.orEmpty().lowercase()
    if (CHALLENGE_MARKERS.any { path.contains(it) || url.host.orEmpty().contains(it) }) return null

    val accept = request.requestHeaders["Accept"].orEmpty()
    val blocked = path.substringAfterLast('.', "") in BLOCKED_EXTENSIONS ||
        accept.startsWith("image/") ||
        accept.startsWith("video/") ||
        accept.startsWith("audio/") ||
        accept.startsWith("font/")
    if (!blocked) return null
    return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
