package com.wander.android.data.sources.deezer

import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedArtist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    suspend fun searchAlbums(query: String, limit: Int = 20): Result<List<UnifiedAlbum>> = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("album")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .build()
        fetchJson(url.toString()).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseAlbumList(data)
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 20): Result<List<UnifiedArtist>> = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("artist")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .build()
        fetchJson(url.toString()).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            data.mapNotNull { it.jsonObject.let(DeezerParsing::parseArtist) }
        }
    }

    suspend fun getTrack(trackId: String): Result<UnifiedTrack?> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("deezer:")
        val url = "$BASE_URL/track/$cleanId"
        fetchJson(url).map { root ->
            DeezerParsing.parseTrack(root.jsonObject)
        }
    }

    suspend fun getAlbum(albumId: String): Result<UnifiedAlbum?> = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("deezer:")
        val url = "$BASE_URL/album/$cleanId"
        fetchJson(url).map { root ->
            DeezerParsing.parseAlbum(root.jsonObject)
        }
    }

    suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("deezer:")
        val url = "$BASE_URL/album/$cleanId/tracks"
        fetchJson(url).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data)
        }
    }

    suspend fun getArtist(artistId: String): Result<ArtistDetails> = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("deezer:")
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

    suspend fun getTrackRadio(trackId: String): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("deezer:")
        val url = "$BASE_URL/track/$cleanId/radio"
        fetchJson(url).map { root ->
            val data = root.jsonObject["data"]?.jsonArray ?: return@map emptyList()
            DeezerParsing.parseTrackList(data)
        }
    }

    private fun fetchJson(url: String): Result<kotlinx.serialization.json.JsonElement> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Deezer API error: ${response.code} for $url")
                }
                val body = response.body?.string() ?: throw IOException("Empty response from $url")
                json.parseToJsonElement(body)
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.deezer.com"
    }
}
