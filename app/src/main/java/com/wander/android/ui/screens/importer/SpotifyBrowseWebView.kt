package com.wander.android.ui.screens.importer

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Spotify web player view, delegating to [ExternalPlatformWebView] with cookie synchronization.
 */
@Composable
internal fun SpotifyBrowseWebView(
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    ExternalPlatformWebView(
        webUrl = "https://open.spotify.com/",
        onWebViewReady = onWebViewReady,
        modifier = modifier
    )
}
