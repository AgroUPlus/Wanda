package com.wander.android.data.importer

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the short link a phone's share sheet produces into the playlist link behind it.
 *
 * `spotify.link` and `deezer.page.link` are what the Spotify and Deezer apps hand out, and neither
 * names a playlist. Only these hosts are ever followed: the text pasted here is arbitrary, and
 * fetching whatever host it names would make the importer a way to make this device request any
 * URL. The address is never logged; it can carry a user's id in its query string.
 */
@Singleton
class ShortLinkExpander @Inject constructor(private val httpClient: HttpClient) {

    /** [input] unchanged when it is not a short link, otherwise the link it redirects to. */
    suspend fun expand(input: String): Result<String> {
        val url = URL_PATTERN.find(input)?.value ?: return Result.success(input)
        if (!isShortLink(url)) return Result.success(input)

        return runCatching {
            val finalUrl = httpClient.get(url) { header("User-Agent", IMPORT_WEB_USER_AGENT) }
                .request.url.toString()
            check(!isShortLink(finalUrl)) { "That short link didn't lead anywhere. Paste the full playlist link instead." }
            finalUrl
        }
    }

    internal companion object {
        private val SHORT_HOSTS = listOf("spotify.link", "link.deezer.com", "deezer.page.link")
        private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

        fun isShortLink(text: String): Boolean {
            val host = PlatformType.hostOfFirstUrl(text) ?: return false
            return SHORT_HOSTS.any { host == it }
        }
    }
}
