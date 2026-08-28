package com.wander.android.data.importer

/**
 * The single browser identity the importer presents to a platform.
 *
 * The sign-in WebView and the HTTP calls that reuse its cookies have to look like the *same*
 * browser: Spotify ties its web session to the user agent that created it, so a WebView logging
 * in as mobile Chrome while the token call claims desktop Chrome gets the session rejected.
 */
internal const val IMPORT_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Mobile Safari/537.36"
