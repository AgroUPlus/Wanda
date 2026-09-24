package com.wander.android.ui.screens.importer

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Runs `fetch()` inside a live WebView's own JS engine rather than this app's HTTP client.
 *
 * Spotify's unofficial web-player API now rejects the identical request from curl or this app's
 * own OkHttp client with `400 "Unauthorized request... under the Spotify Developer Terms"`,
 * cookie or no cookie, real-browser headers or none — evidence this is TLS/client fingerprinting,
 * not a missing credential. A WebView's networking stack is a real browser's, so the same call
 * made from inside its page passes. `open.spotify.com` itself (unlike its login page) already
 * renders correctly in this WebView, so no other page-compatibility work is needed here.
 */
internal suspend fun WebView.fetchText(url: String, headers: Map<String, String> = emptyMap()): String =
    // evaluateJavascript is documented as main-thread-only; the caller may be on Dispatchers.IO
    // (PlaylistParserCoordinator wraps every parser call in it), so this hops over regardless.
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val headersJs = headers.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "${JSONObject.quote(key)}: ${JSONObject.quote(value)}"
            }
            val js = """
                (function () {
                    return fetch(${JSONObject.quote(url)}, { credentials: 'include', headers: $headersJs })
                        .then(function (r) { return r.text(); })
                        .catch(function (e) { return 'WANDA_FETCH_ERROR:' + (e && e.message ? e.message : 'unknown'); });
                })();
            """.trimIndent()
            evaluateJavascript(js) { rawResult ->
                // evaluateJavascript hands back a JSON-encoded string literal (quoted, escaped) for
                // a JS string result, or the 4-character literal "null" if torn down mid-call.
                val decoded = when {
                    rawResult == null || rawResult == "null" -> ""
                    else -> runCatching { JSONTokener(rawResult).nextValue() as? String }.getOrNull() ?: ""
                }
                if (cont.isActive) cont.resume(decoded)
            }
        }
    }
