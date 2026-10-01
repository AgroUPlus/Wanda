package com.wander.android.data.podcast

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PodcastHit(
    val title: String,
    val feedUrl: String,
    val author: String?,
    val artworkUrl: String?
)

/** Thrown, never returned as an empty list, when a search is attempted without the listener's consent or key. */
class PodcastIndexUnavailableException(message: String) : IllegalStateException(message)

/**
 * Searches the open PodcastIndex directory (api.podcastindex.org) by keyword.
 *
 * Never called without `SecureStorage.isPodcastIndexEnabled`: a search sends what the listener typed
 * and their IP address to a third party. It is also refused here, not only in the UI, so no other
 * caller can reach the network around the setting. Only the search term is sent; the listener's
 * subscriptions never are.
 *
 * PodcastIndex asks each app user for their own free API key and secret, kept in [SecureStorage].
 * Each request is signed with SHA-1 of key + secret + the current Unix time, which is the scheme the
 * service defines.
 */
@Singleton
class PodcastIndexClient @Inject constructor(
    private val client: OkHttpClient,
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(term: String): Result<List<PodcastHit>> = withContext(Dispatchers.IO) {
        if (!secureStorage.isPodcastIndexEnabled.value) {
            return@withContext Result.failure(PodcastIndexUnavailableException("Podcast search is switched off"))
        }
        val key = secureStorage.podcastIndexKey
        val secret = secureStorage.podcastIndexSecret
        if (key.isBlank() || secret.isBlank()) {
            return@withContext Result.failure(PodcastIndexUnavailableException("No PodcastIndex key is set"))
        }
        try {
            Result.success(request(term, key, secret))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun request(term: String, key: String, secret: String): List<PodcastHit> {
        val date = (System.currentTimeMillis() / MILLIS_PER_SECOND).toString()
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("q", term)
            .addQueryParameter("max", MAX_RESULTS.toString())
            .build()
        val request = Request.Builder().url(url)
            .header("X-Auth-Date", date)
            .header("X-Auth-Key", key)
            .header("Authorization", sha1Hex(key + secret + date))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                throw IOException("PodcastIndex rejected the API key")
            }
            if (!response.isSuccessful) throw IOException("PodcastIndex answered HTTP ${response.code}")
            val body = response.body.string()
            val parsed = try {
                json.decodeFromString<SearchResponse>(body)
            } catch (e: SerializationException) {
                throw IOException("PodcastIndex sent an answer this app cannot read")
            }
            return parsed.feeds.mapNotNull { feed ->
                val feedUrl = feed.url.trim()
                val title = feed.title.trim()
                if (feedUrl.isEmpty() || title.isEmpty()) return@mapNotNull null
                PodcastHit(
                    title = title,
                    feedUrl = feedUrl,
                    author = feed.author?.trim()?.ifEmpty { null },
                    artworkUrl = (feed.artwork ?: feed.image)?.trim()?.ifEmpty { null }
                )
            }
        }
    }

    // SHA-1 is what the PodcastIndex API defines for its Authorization header; it is a request
    // signature checked by their server, not something this app relies on for security.
    private fun sha1Hex(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8)) // NOSONAR: mandated by the API
            .joinToString("") { "%02x".format(it) }

    @Serializable
    private data class SearchResponse(val feeds: List<Feed> = emptyList())

    @Serializable
    private data class Feed(
        val title: String = "",
        val url: String = "",
        val author: String? = null,
        val image: String? = null,
        val artwork: String? = null
    )

    private companion object {
        const val ENDPOINT = "https://api.podcastindex.org/api/1.0/search/byterm"
        const val MAX_RESULTS = 30
        const val MILLIS_PER_SECOND = 1000L
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
