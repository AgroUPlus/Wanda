package com.wander.android.data.sources.deezer

import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public Deezer REST API client (api.deezer.com).
 *
 * Provides fast, unauthenticated queries for catalog search, artist profiles,
 * albums, track listings, and global charts.
 */
@Singleton
class DeezerApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchTracks(query: String, limit: Int = 30): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .build()
        fetchJson(url.toString()).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data)
        }
    }

    suspend fun getTrack(trackId: String): Result<UnifiedTrack?> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix(ID_PREFIX)
        val url = "$BASE_URL/track/$cleanId"
        fetchJson(url).map { root ->
            DeezerParsing.parseTrack(root.jsonObject)
        }
    }

    suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix(ID_PREFIX)
        val url = "$BASE_URL/album/$cleanId/tracks"
        fetchJson(url).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data)
        }
    }

    suspend fun getArtist(artistId: String): Result<ArtistDetails> = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix(ID_PREFIX)
        val artistUrl = "$BASE_URL/artist/$cleanId"
        val topTracksUrl = "$BASE_URL/artist/$cleanId/top?limit=20"
        val albumsUrl = "$BASE_URL/artist/$cleanId/albums?limit=25"

        val artistJson = fetchJson(artistUrl).getOrElse { return@withContext Result.failure(it) }.jsonObject
        val topTracks = fetchJson(topTracksUrl).map {
            DeezerParsing.parseTrackList(it.jsonObject["data"]?.jsonArray ?: return@map emptyList())
        }.getOrDefault(emptyList())
        val albums = fetchJson(albumsUrl).map {
            DeezerParsing.parseAlbumList(it.jsonObject["data"]?.jsonArray ?: return@map emptyList())
        }.getOrDefault(emptyList())

        Result.success(DeezerParsing.parseArtistDetails(artistJson, topTracks, albums))
    }

    suspend fun getChartTracks(): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/chart/0/tracks?limit=30"
        fetchJson(url).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data)
        }
    }

    /** [count] is asked for and enforced: the endpoint answers with its own default size. */
    suspend fun getTrackRadio(trackId: String, count: Int): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix(ID_PREFIX)
        val url = "$BASE_URL/track/$cleanId/radio?limit=$count"
        fetchJson(url).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data).take(count)
        }
    }

    /**
     * One API call's JSON, or a failure.
     *
     * Deezer reports its own errors — a quota hit, an id that names nothing — as HTTP 200 with an
     * `error` object in place of the data. Read as data, that came out as an empty list: a search
     * throttled into silence looked exactly like a search with no matches. The URL is kept out of
     * the messages, since it carries the user's query.
     */
    private fun fetchJson(url: String): Result<JsonElement> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Deezer API error: ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response from Deezer API")
                json.parseToJsonElement(body).also { root ->
                    apiError(root)?.let { throw it }
                }
            }
        }
    }

    internal companion object {
        private const val BASE_URL = "https://api.deezer.com"
        private val ID_PREFIX = SourceType.DEEZER.idPrefix

        /** The error a 200 response carries instead of data, e.g. `{"error":{"code":4,"message":"Quota limit exceeded"}}`. */
        fun apiError(root: JsonElement): IOException? {
            val error = (root as? JsonObject)?.get("error") as? JsonObject ?: return null
            val code = error["code"]?.jsonPrimitive?.intOrNull
            val message = error["message"]?.jsonPrimitive?.contentOrNull
            val type = error["type"]?.jsonPrimitive?.contentOrNull
            val detail = listOfNotNull(type, message, code?.let { "code $it" })
                .joinToString(": ")
                .takeIf { it.isNotEmpty() }
                ?.let { " ($it)" }
                .orEmpty()
            return IOException("Deezer API refused the request$detail")
        }
    }
}
