package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.ytmusic.youTubeVideoId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** The path the redirect page is served at, on whichever domain the user configured. */
internal const val LISTEN_PATH = "listen"

/** Matches the `wanda` scheme declared in the manifest, which the redirect page links to. */
internal const val APP_SCHEME = "wanda"

/**
 * Sends share links through a domain of the user's own, when they have set one.
 *
 * The rewrite is deliberately silent in both directions. A link that can be carried through the
 * domain is; anything else is shared exactly as its backend minted it, with no warning and no
 * refusal — the user asked for their domain on their links, not for a lecture about the ones it
 * cannot carry.
 */
@Singleton
class ShareLinkRewriter @Inject constructor(
    private val secureStorage: SecureStorage,
    private val agroGraphQl: AgroGraphQl
) {

    /**
     * The domain in force: a paired Agro server's, if it has one configured, otherwise whatever
     * this device was told directly.
     */
    internal fun domain(): String =
        secureStorage.agroShareDomain.value.ifBlank { secureStorage.shareDomain.value }

    suspend fun rewrite(url: String): String {
        val domain = domain()
        if (domain.isBlank()) return url

        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (!uri.isAllowed()) return url

        // When Agro is paired, mint a short link UID instead of placing raw URLs or video IDs in query params
        if (agroGraphQl.isConfigured) {
            val shortId = runCatching {
                // Attributed to the account, and tagged with which backend it came from. Without
                // the owner the link could be minted but never listed, counted or revoked in
                // Agro's link manager; without the source, deleting it there cannot say whether
                // anything is left behind on Navidrome.
                val mutation = "mutation CreateShortLink(\$userId: String, \$targetUrl: String!, " +
                    "\$source: String) { createShortLink(userId: \$userId, targetUrl: " +
                    "\$targetUrl, source: \$source) }"
                val vars = buildJsonObject {
                    put("userId", agroGraphQl.userId)
                    put("targetUrl", url)
                    put("source", sourceOf(uri))
                }
                agroGraphQl.execute(mutation, vars).getOrNull()
                    ?.get("createShortLink")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (!shortId.isNullOrBlank()) {
                return "https://$domain/$LISTEN_PATH?id=$shortId"
            }
        }

        // Fallback for YouTube links when Agro is not paired
        youTubeVideoId(uri)?.let { videoId ->
            return "https://$domain/$LISTEN_PATH?v=$videoId"
        }

        val target = URLEncoder.encode(url, Charsets.UTF_8.name())
        return "https://$domain/$LISTEN_PATH?u=$target"
    }

    /**
     * The real link behind one of our short links, or null when [uri] is not one.
     *
     * Two forms arrive: `https://<domain>/listen?…` when the user taps their own link with Wanda
     * installed, and `wanda://listen?…` from the redirect page's "open in Wanda" button — the page
     * cannot know the app's domain verification state, so it offers the app scheme instead.
     *
     * The unwrapped target is put through the same allowlist as the wrap. A short link is a URL a
     * stranger can type, and following whatever it carries would make the app the open redirect
     * the wrapping side is careful not to be.
     */
    fun unwrap(uri: Uri): Uri? {
        if (!uri.isShortLink()) return null
        uri.getQueryParameter("v")
            ?.let { return Uri.parse("https://music.youtube.com/watch?v=$it") }
        val target = uri.getQueryParameter("u")
            ?.let { runCatching { Uri.parse(URLDecoder.decode(it, Charsets.UTF_8.name())) } }
            ?.getOrNull()
            ?: return null
        return target.takeIf { it.isAllowed() }
    }

    private fun Uri.isShortLink(): Boolean {
        val isListen = pathSegments.firstOrNull() == LISTEN_PATH || host == LISTEN_PATH
        if (!isListen) return false
        return when (scheme?.lowercase()) {
            APP_SCHEME -> true
            "https" -> host?.lowercase() == domain().ifBlank { null }
            else -> false
        }
    }

    /**
     * Whether a URL is one of ours to forward.
     *
     * `https` only — an `http` target would be a downgrade the recipient never agreed to — and the
     * host must either be YouTube's or the user's own music server, which is the only other place
     * this app mints links for.
     */
    /**
     * Which backend a link points at, as Agro's link manager records it.
     *
     * Only `"navidrome"` is acted on there — it is the one case where deleting the link leaves a
     * share behind on another server. Everything else is a plain forwarding link that exists
     * nowhere but Agro.
     */
    private fun sourceOf(uri: Uri): String? {
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
        return when {
            host == null -> null
            host == navidromeHost() -> "navidrome"
            host in YOUTUBE_HOSTS -> "ytmusic"
            else -> null
        }
    }

    internal fun Uri.isAllowed(): Boolean {
        if (!scheme.equals("https", ignoreCase = true)) return false
        val host = host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return false
        if (host in YOUTUBE_HOSTS) return true
        if (host == navidromeHost()) return true
        // Whatever the server will forward to, so the two ends of the same feature agree: a link
        // this app wraps is one the page at the other end is prepared to send a visitor to.
        return host in agroHosts()
    }

    private fun agroHosts(): Set<String> = secureStorage.agroShareHosts
        .split(',')
        .mapNotNullTo(mutableSetOf()) { it.trim().lowercase().takeIf(String::isNotBlank) }

    /** Host of the configured Navidrome server, so its share links pass and nothing else does. */
    private fun navidromeHost(): String? = runCatching {
        Uri.parse(secureStorage.navidromeServerUrl).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        val YOUTUBE_HOSTS = setOf("youtube.com", "music.youtube.com", "youtu.be")
    }
}
