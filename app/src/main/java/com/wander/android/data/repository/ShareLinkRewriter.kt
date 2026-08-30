package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.core.playback.SpeedAndPitch
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
 * Sends share links through the best address the user has, in that order.
 *
 * **The minting half of Agro's `SHARE_LINKS.md`, which is normative.** The tier order, the
 * parameter names and the speed/pitch bounds are specified there and implemented again in Rust
 * (`agro/src/listen.rs`) and JavaScript (`frwd.top/listen/index.html`). Nothing checks that the
 * three agree, so a change to any of them is a change to the document first.
 *
 * Three tiers, most personal first:
 *
 * 1. **A domain of their own**, when one is set — theirs is the name on the link.
 * 2. **Their Agro server**, when one is paired. It serves `/listen` itself, so this needs no
 *    custom domain and no extra endpoint; it only needs the server to be reachable by whoever
 *    opens the link. Previously missing entirely: the rewrite bailed to the backend's URL the
 *    moment no domain was set, so a paired Agro with no domain got nothing.
 * 3. **Whatever the backend minted** — Navidrome's share, YouTube's watch URL.
 *
 * The rewrite is deliberately silent in every direction. A link that can be carried is; anything
 * else is shared exactly as its backend minted it, with no warning and no refusal — the user asked
 * for their address on their links, not for a lecture about the ones it cannot carry.
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

    suspend fun rewrite(
        url: String,
        speedPitch: SpeedAndPitch = SpeedAndPitch(),
        expiresAt: Long? = null
    ): String {
        val base = shareBase() ?: return url

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
                    "\$source: String, \$expiresAt: Int) { createShortLink(userId: \$userId, targetUrl: " +
                    "\$targetUrl, source: \$source, expiresAt: \$expiresAt) }"
                val vars = buildJsonObject {
                    put("userId", agroGraphQl.userId)
                    put("targetUrl", url)
                    put("source", sourceOf(uri))
                    if (expiresAt != null) {
                        put("expiresAt", expiresAt)
                    }
                }
                agroGraphQl.execute(mutation, vars).getOrNull()
                    ?.get("createShortLink")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (!shortId.isNullOrBlank()) {
                return "$base/$LISTEN_PATH?id=$shortId".withPlayback(speedPitch)
            }
        }

        // Fallback for YouTube links when Agro is not paired
        youTubeVideoId(uri)?.let { videoId ->
            return "$base/$LISTEN_PATH?v=$videoId".withPlayback(speedPitch)
        }

        val target = URLEncoder.encode(url, Charsets.UTF_8.name())
        return "$base/$LISTEN_PATH?u=$target".withPlayback(speedPitch)
    }

    /**
     * The origin share links are minted on, or null when there is nowhere but the backend.
     *
     * The Agro tier keeps whatever scheme the server was configured with rather than forcing
     * `https`, so a link is minted on the address the server actually answers on.
     *
     * In practice that scheme is almost always `https`: `network_security_config.xml` permits
     * cleartext only for `localhost`, `127.0.0.1` and `10.0.2.2`, so a plain-`http` Agro on the
     * LAN is not reachable by this app at all and never becomes a configured server. The `http`
     * branch therefore exists for the `adb reverse` development flow, not for self-hosting — a
     * self-hosted Agro needs a certificate, which is what the network config documents.
     */
    private fun shareBase(): String? {
        domain().takeIf { it.isNotBlank() }?.let { return "https://$it" }
        if (!agroGraphQl.isConfigured) return null
        val server = runCatching { Uri.parse(secureStorage.agroServerUrl) }.getOrNull() ?: return null
        val scheme = server.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val authority = server.authority?.takeIf(String::isNotBlank) ?: return null
        return "$scheme://$authority"
    }

    /**
     * Carries the listener's speed and pitch on the link, when they are not the defaults.
     *
     * Sharing a track you are playing at 1.25x and a tone lower is sharing *that* — the version
     * you meant, not the one the file happens to hold. Omitted entirely at the defaults so an
     * ordinary share stays an ordinary URL.
     */
    private fun String.withPlayback(speedPitch: SpeedAndPitch): String =
        this + playbackSuffix(speedPitch)

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
            // Both minting tiers, or a link this device made would not be one it can open.
            "http", "https" -> host?.lowercase() in ownHosts()
            else -> false
        }
    }

    /** The hosts this device mints links on: the user's domain, and their Agro server. */
    private fun ownHosts(): Set<String> = setOfNotNull(
        domain().lowercase().takeIf(String::isNotBlank),
        runCatching { Uri.parse(secureStorage.agroServerUrl).host?.lowercase() }
            .getOrNull()?.takeIf(String::isNotBlank)
    )

    /**
     * The speed and pitch a link asks to be played at, or null when it carries neither.
     *
     * Read off the wrapper rather than the unwrapped target: the target is the backend's own URL
     * and knows nothing about how the person sharing it was listening. Values outside the range
     * the player accepts are dropped rather than clamped — a link asking for 40x is not a link
     * whose intent is recoverable.
     */
    fun playbackOf(uri: Uri): SpeedAndPitch? {
        if (!uri.isShortLink()) return null
        return playbackFrom(uri.getQueryParameter("s"), uri.getQueryParameter("p"))
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

/**
 * The `&s=…&p=…` a minted link carries, or an empty string at the defaults.
 *
 * `SHARE_LINKS.md` §3.2. A top-level function rather than a method so the rule can be tested
 * without an Android `Uri` — the spec is implemented three times in three languages, and this is
 * the half of it that can be pinned down by a plain unit test.
 */
internal fun playbackSuffix(speedPitch: SpeedAndPitch): String =
    if (speedPitch.isDefault) ""
    else "&s=${speedPitch.speed}&p=${speedPitch.pitch}"

/**
 * The speed and pitch a link asks for, or null when it carries none this player will honour.
 *
 * `SHARE_LINKS.md` §3.2: both or neither, both within [SpeedAndPitch.RANGE], and **dropped rather
 * than clamped** when out of range — a link asking for 40x is not one whose intent is recoverable.
 * Takes the raw strings so the parsing and the bounds are testable together.
 */
internal fun playbackFrom(speed: String?, pitch: String?): SpeedAndPitch? {
    val s = speed?.toFloatOrNull() ?: return null
    val p = pitch?.toFloatOrNull() ?: return null
    if (s !in SpeedAndPitch.RANGE || p !in SpeedAndPitch.RANGE) return null
    return SpeedAndPitch(speed = s, pitch = p).takeIf { !it.isDefault }
}
