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
        val accessToken = session.accessToken

        val playlistsResponse: String = httpClient.get("https://api.spotify.com/v1/me/playlists?limit=50") {
            header("Authorization", "Bearer $accessToken")
            header(HEADER_USER_AGENT, IMPORT_WEB_USER_AGENT)
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()

        val root = json.parseToJsonElement(playlistsResponse).jsonObject
        val items = root["items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

        items.mapNotNull { item ->
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

    suspend fun parse(url: String, cookie: String? = null): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Spotify playlist ID in the link.")

        // Step 1: Obtain web access token from Spotify
        val accessToken = fetchWebSession(cookie).accessToken

        // Step 2: Fetch playlist details
        val playlistResponse: String = httpClient.get("https://api.spotify.com/v1/playlists/$playlistId") {
            header("Authorization", "Bearer $accessToken")
            header(HEADER_USER_AGENT, IMPORT_WEB_USER_AGENT)
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()

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

        RawImportPlaylist(
            platform = PlatformType.SPOTIFY,
            title = title,
            description = description,
            coverUrl = coverUrl,
            tracks = tracks
        )
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
