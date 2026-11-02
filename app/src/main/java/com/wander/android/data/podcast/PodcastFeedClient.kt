package com.wander.android.data.podcast

import android.util.Xml
import com.wander.android.core.database.entity.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FeedFetch {
    /** Where a permanent redirect (301, 308) says the feed now lives, or null when it has not moved. */
    val movedTo: String?

    /** The feed answered 304: nothing changed since the validators we sent. */
    data class NotModified(override val movedTo: String?) : FeedFetch

    data class Updated(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
        override val movedTo: String?
    ) : FeedFetch
}

/**
 * Downloads and parses one feed through the app's shared client, so the HTTPS-only rule for public
 * hosts applies to it like to everything else. Never logs the URL: private feeds carry a listener
 * token in the query string.
 */
@Singleton
class PodcastFeedClient @Inject constructor(private val client: OkHttpClient) {

    /**
     * Conditional when [known] carries validators, which makes an unchanged feed a few hundred bytes
     * instead of a re-download.
     *
     * @throws IOException on a transport failure, a non-2xx answer, an oversized body or [FeedParseException].
     */
    suspend fun fetch(feedUrl: String, known: PodcastEntity? = null): FeedFetch = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(feedUrl).apply {
            known?.etag?.let { header("If-None-Match", it) }
            known?.lastModified?.let { header("If-Modified-Since", it) }
        }.build()

        client.newCall(request).execute().use { response ->
            val movedTo = permanentRedirectTarget(response)
            if (response.code == HTTP_NOT_MODIFIED) return@use FeedFetch.NotModified(movedTo)
            if (!response.isSuccessful) throw IOException("The feed answered HTTP ${response.code}")
            val source = response.body.source()
            source.request(MAX_FEED_BYTES + 1)
            if (source.buffer.size > MAX_FEED_BYTES) throw IOException("The feed is larger than 10 MB")

            val parser = Xml.newPullParser()
            val feed = try {
                parser.setInput(source.inputStream(), null)
                RssFeedParser.parse(parser)
            } catch (e: XmlPullParserException) {
                throw FeedParseException("The feed is not valid XML")
            }
            FeedFetch.Updated(
                feed = feed,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                movedTo = movedTo
            )
        }
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val MAX_FEED_BYTES = 10L * 1024 * 1024
    }
}
