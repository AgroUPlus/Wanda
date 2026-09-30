package com.wander.android.data.importer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.serialization.json.booleanOrNull

/**
 * A raw HTTP call — this app's own OkHttp client, or plain curl — to Spotify's unofficial web-player
 * token endpoint now gets `400 "Unauthorized request... under the Spotify Developer Terms"`
 * regardless of cookies or browser-realistic headers, which is TLS/client fingerprinting rather than
 * a missing credential: nothing this parser can add to a request fixes it. [SpotifyWebFetch] runs
 * the identical call through a live WebView's own `fetch()` instead, which carries a real browser's
 * network stack and passes. The `...ViaFetcher` methods below share this class's parsing with that
 * path; [fetchUserPlaylists] and [parse] are kept exactly as they were — still correct, still
 * tested — for whichever day this endpoint stops requiring that.
 */
@Singleton
class SpotifyPlaylistParser @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""(?:spotify:playlist:|spotify\.com/(?:[a-zA-Z-]+/)?playlist/)([a-zA-Z0-9]+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    suspend fun fetchUserPlaylists(cookie: String? = null): Result<List<RawUserPlaylistSummary>> = runCatching {
        val session = fetchWebSession(cookie)
        check(!session.isAnonymous) { "Please sign in to Spotify in the browser above." }

        val playlistsResponse: String = httpClient.get("https://api.spotify.com/v1/me/playlists?limit=50") {
            header("Authorization", "Bearer ${session.accessToken}")
            header(HEADER_USER_AGENT, IMPORT_WEB_USER_AGENT)
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()

        parsePlaylistsResponse(playlistsResponse)
    }

    suspend fun parse(url: String, cookie: String? = null): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Spotify playlist ID in the link.")

        val accessToken = fetchWebSession(cookie).accessToken

        val playlistResponse: String = httpClient.get("https://api.spotify.com/v1/playlists/$playlistId") {
            header("Authorization", "Bearer $accessToken")
            header(HEADER_USER_AGENT, IMPORT_WEB_USER_AGENT)
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()

        parsePlaylistResponse(playlistResponse)
    }

    /**
     * Same call [parse] makes, sourced from a real browser's `fetch()` (see [SpotifyWebFetch])
     * instead of this app's own HTTP client. [fetch] takes a URL and the headers to send, and
     * returns the response body text.
     */
    suspend fun parseViaFetcher(
        url: String,
        fetch: suspend (String, Map<String, String>) -> String
    ): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Spotify playlist ID in the link.")

        val accessToken = accessTokenViaFetcher(fetch)
        val playlistResponse = fetch(
            "https://api.spotify.com/v1/playlists/$playlistId",
            mapOf("Authorization" to "Bearer $accessToken")
        )
        parsePlaylistResponse(playlistResponse)
    }

    /** Same call [fetchUserPlaylists] makes, sourced from [fetch]. See [parseViaFetcher]. */
    suspend fun fetchUserPlaylistsViaFetcher(
        fetch: suspend (String, Map<String, String>) -> String
    ): Result<List<RawUserPlaylistSummary>> = runCatching {
        val tokenJson = tokenJsonViaFetcher(fetch)
        val isAnonymous = tokenJson["isAnonymous"]?.jsonPrimitive?.booleanOrNull
            ?: (tokenJson["isAnonymous"]?.jsonPrimitive?.content == "true")
        check(!isAnonymous) { "Please sign in to Spotify in the browser above." }
        val accessToken = tokenJson["accessToken"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Spotify did not provide an access token.")

        val playlistsResponse = fetch(
            "https://api.spotify.com/v1/me/playlists?limit=50",
            mapOf("Authorization" to "Bearer $accessToken")
        )
        parsePlaylistsResponse(playlistsResponse)
    }

    private suspend fun accessTokenViaFetcher(fetch: suspend (String, Map<String, String>) -> String): String {
        val tokenJson = tokenJsonViaFetcher(fetch)
        return tokenJson["accessToken"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Spotify did not provide an access token.")
    }

    private suspend fun tokenJsonViaFetcher(
        fetch: suspend (String, Map<String, String>) -> String
    ): kotlinx.serialization.json.JsonObject {
        val body = fetch(TOKEN_URL, mapOf("Accept" to "application/json"))
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("Spotify returned an unexpected token response.")
    }

    private fun parsePlaylistResponse(playlistResponse: String): RawImportPlaylist {
        val root = json.parseToJsonElement(playlistResponse).jsonObject
        val title = root["name"]?.jsonPrimitive?.content ?: "Imported Spotify Playlist"
        val description = root["description"]?.jsonPrimitive?.content
        val coverUrl = root["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

        val tracks = mutableListOf<RawImportTrack>()
        val tracksObj = root["tracks"]?.jsonObject
        val items = tracksObj?.get("items")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

        for (item in items) {
            val trackObj = item.jsonObject["track"]?.jsonObject ?: continue
            val trackName = trackObj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
            val artists = trackObj["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                ?.joinToString(", ")
                ?: "Unknown Artist"
            val albumName = trackObj["album"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L

            tracks.add(
                RawImportTrack(
                    title = trackName,
                    artist = artists,
                    album = albumName,
                    durationMs = durationMs
                )
            )
        }

        check(tracks.isNotEmpty()) { "No tracks could be found in this Spotify playlist." }

        return RawImportPlaylist(
            platform = PlatformType.SPOTIFY,
            title = title,
            description = description,
            coverUrl = coverUrl,
            tracks = tracks
        )
    }

    private fun parsePlaylistsResponse(playlistsResponse: String): List<RawUserPlaylistSummary> {
        val root = json.parseToJsonElement(playlistsResponse).jsonObject
        val items = root["items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

        return items.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: "Playlist"
            val desc = obj["description"]?.jsonPrimitive?.content
            val coverUrl = obj["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
            val count = obj["tracks"]?.jsonObject?.get("total")?.jsonPrimitive?.longOrNull?.toInt() ?: 0

            RawUserPlaylistSummary(
                id = id,
                name = name,
                description = desc,
                coverUrl = coverUrl,
                trackCount = count,
                platform = PlatformType.SPOTIFY,
                url = "https://open.spotify.com/playlist/$id"
            )
        }
    }

    private data class WebSession(val accessToken: String, val isAnonymous: Boolean)

    /**
     * The web player's token endpoint. The older `/get_access_token` path Spotify used to expose
     * is gone, so a call to it comes back as an HTML error page rather than JSON.
     */
    private suspend fun fetchWebSession(cookie: String?): WebSession {
        val response = httpClient.get(TOKEN_URL) {
            header(HEADER_USER_AGENT, IMPORT_WEB_USER_AGENT)
            header("Accept", "application/json")
            header("Referer", "https://open.spotify.com/")
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Spotify refused the token request (HTTP ${response.status.value})."
        }

        val tokenJson = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("Spotify returned an unexpected token response.")
        val accessToken = tokenJson["accessToken"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Spotify did not provide an access token.")
        val isAnonymous = tokenJson["isAnonymous"]?.jsonPrimitive?.booleanOrNull
            ?: (tokenJson["isAnonymous"]?.jsonPrimitive?.content == "true")

        return WebSession(accessToken = accessToken, isAnonymous = isAnonymous)
    }

    private companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val TOKEN_URL = "https://open.spotify.com/api/token?reason=init&productType=web_player"
    }
}
