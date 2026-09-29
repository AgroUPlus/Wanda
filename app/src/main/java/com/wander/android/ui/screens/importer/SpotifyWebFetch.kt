package com.wander.android.ui.screens.importer

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** How long a page's own `fetch()` gets before this gives up on it. */
private const val FETCH_TIMEOUT_MS = 20_000L

/**
 * Runs `fetch()` inside a live WebView's own JS engine rather than this app's HTTP client.
 *
 * Spotify's unofficial web-player API now rejects the identical request from curl or this app's
 * own OkHttp client with `400 "Unauthorized request... under the Spotify Developer Terms"`,
 * cookie or no cookie, real-browser headers or none — evidence this is TLS/client fingerprinting,
 * not a missing credential. A WebView's networking stack is a real browser's, so the same call
 * made from inside its page passes. `open.spotify.com` itself (unlike its login page) already
 * renders correctly in this WebView, so no other page-compatibility work is needed here.
 *
 * [withTimeout] wraps the whole thing: `evaluateJavascript`'s callback never fires at all if the
 * WebView is torn down mid-call (the screen navigated away, the process trimmed it) or if the page
 * itself never settles, and a `suspendCancellableCoroutine` with nothing else bounding it then
 * waits forever — the caller's "loading playlist" state simply never resolves, which reads as the
 * import silently doing nothing rather than as the clear failure it actually is.
 */
internal suspend fun WebView.fetchText(url: String, headers: Map<String, String> = emptyMap()): String =
    try {
        withTimeout(FETCH_TIMEOUT_MS) {
            // evaluateJavascript is documented as main-thread-only; the caller may be on
            // Dispatchers.IO (PlaylistParserCoordinator wraps every parser call in it), so this
            // hops over regardless.
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
                        // evaluateJavascript hands back a JSON-encoded string literal (quoted,
                        // escaped) for a JS string result, or the 4-character literal "null" if
                        // torn down mid-call.
                        val decoded = when {
                            rawResult == null || rawResult == "null" -> ""
                            else -> runCatching { JSONTokener(rawResult).nextValue() as? String }.getOrNull() ?: ""
                        }
                        if (cont.isActive) cont.resume(decoded)
                    }
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw IllegalStateException("Spotify's page did not respond in time. Try reloading it above.", e)
    }
