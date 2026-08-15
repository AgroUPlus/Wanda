package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.ytmusic.youTubeVideoId
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
 *
 * That silence is what makes the allowlist safe to enforce strictly. A redirector that will
 * forward to any URL handed to it is an open redirect: a phishing link wearing the user's own
 * domain, and the domain's reputation paying for it. So only links this app could have produced
 * are ever wrapped, and the set of those is derived from the backends actually configured rather
 * than from a list somebody has to remember to update.
 */
@Singleton
class ShareLinkRewriter @Inject constructor(
    private val secureStorage: SecureStorage
) {

    fun rewrite(url: String): String {
        val domain = secureStorage.shareDomain.value
        if (domain.isBlank()) return url

        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (!uri.isAllowed()) return url

        // A YouTube link keeps its video id in the open: it is already public, it is what makes
        // the short link readable, and it means the page can offer "open in YouTube Music"
        // without unpacking anything.
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
            "https" -> host?.lowercase() == secureStorage.shareDomain.value.ifBlank { null }
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
    internal fun Uri.isAllowed(): Boolean {
        if (!scheme.equals("https", ignoreCase = true)) return false
        val host = host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return false
        if (host in YOUTUBE_HOSTS) return true
        return host == navidromeHost()
    }

    /** Host of the configured Navidrome server, so its share links pass and nothing else does. */
    private fun navidromeHost(): String? = runCatching {
        Uri.parse(secureStorage.navidromeServerUrl).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        val YOUTUBE_HOSTS = setOf("youtube.com", "music.youtube.com", "youtu.be")
    }
}
