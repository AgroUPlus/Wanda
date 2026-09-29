package com.wander.android.data.importer

import android.util.Log
import com.wander.android.data.sources.deezer.DeezerAccountManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerPlaylistParser @Inject constructor(
    private val httpClient: HttpClient,
    private val accountManager: DeezerAccountManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""deezer\.com/(?:[a-zA-Z-]+/)?playlist/(\d+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    /**
     * Lists the signed-in user's own playlists, the same `arl` session [DeezerAccountManager] holds
     * for streaming.
     *
     * Deezer's private web gateway (`deezer.pageProfile`) is tried first since it's the only way to
     * see *private* playlists — the default for most personal ones — but it's unofficial and
     * undocumented, so its exact response shape is a best-effort reading of it, not a guarantee.
     * [fetchPublicPlaylists], Deezer's documented (but public-playlists-only) REST endpoint, is the
     * fallback so a wrong guess about the private shape degrades to "public playlists only" rather
     * than failing outright.
     */
    suspend fun fetchUserPlaylists(): Result<List<RawUserPlaylistSummary>> = runCatching {
        val arl = accountManager.arl
        check(arl.isNotBlank()) { "Please sign in to Deezer first." }

        val (userId, checkForm) = fetchUserSession(arl).getOrElse {
            throw IllegalStateException("Could not read your Deezer account — try signing in again.")
        }

        val privatePlaylists = runCatching { fetchPrivatePlaylists(arl, userId, checkForm) }.getOrNull()
        if (!privatePlaylists.isNullOrEmpty()) return@runCatching privatePlaylists

        fetchPublicPlaylists(userId).getOrThrow()
    }

    /**
     * [deezer.getUserData] needs no token itself, but almost every other gw-light method does —
     * `page.get` answers `VALID_TOKEN_REQUIRED` outright without one. `checkForm` here is that
     * token, scoped to this `arl` session rather than to any one request.
     */
    private suspend fun fetchUserSession(arl: String): Result<Pair<String, String>> = runCatching {
        val response: String = httpClient.post("$GW_LIGHT_URL?method=deezer.getUserData&api_version=1.0&api_token=") {
            header("Cookie", "arl=$arl")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.body()
        val results = json.parseToJsonElement(response).jsonObject["results"]?.jsonObject
            ?: throw IllegalStateException("Invalid Deezer user data payload")
        val userId = results["USER"]?.jsonObject?.get("USER_ID")?.jsonPrimitive?.content
            ?: throw IllegalStateException("No user id in Deezer user data")
        val checkForm = results["checkForm"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No CSRF token in Deezer user data")
        userId to checkForm
    }

    /**
     * Best-effort reading of an undocumented endpoint — see [fetchUserPlaylists]'s own doc.
     *
     * `deezer.pageProfile` is what an anonymous probe of this gateway suggested (it accepted the
     * request shape and answered `results: false` rather than a hard error, unlike every PAGE name
     * tried against the more generic `page.get`) but its exact response shape for a *real* signed-in
     * profile has never actually been observed — logged at [Log.d] so a failure here is diagnosable
     * from a real device's logcat instead of another guess.
     */
    private suspend fun fetchPrivatePlaylists(arl: String, userId: String, checkForm: String): List<RawUserPlaylistSummary> {
        val payload = buildString {
            append("""{"USER_ID":"""")
            append(userId)
            append("""","tab":"playlists","lang":"en"}""")
        }
        val response: String = httpClient.post("$GW_LIGHT_URL?method=deezer.pageProfile&api_version=1.0&api_token=$checkForm") {
            header("Cookie", "arl=$arl")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
        val results = json.parseToJsonElement(response).jsonObject["results"]?.jsonObject
        if (results == null) {
            Log.d(TAG, "pageProfile returned no results object: $response")
            return emptyList()
        }
        val items = findPlaylistItems(results)
        if (items.isEmpty()) Log.d(TAG, "pageProfile returned no recognizable playlist items: $response")
        return items.mapNotNull { parsePrivatePlaylistItem(it) }
    }

    /** Tries every shape a playlist list plausibly arrives in under `results`. */
    private fun findPlaylistItems(results: JsonObject): List<JsonObject> {
        val candidates = listOfNotNull(
            results["TAB"]?.jsonObject?.get("playlists")?.jsonArray,
            results["PLAYLISTS"]?.jsonObject?.get("data")?.jsonArray,
            results["PLAYLISTS"] as? JsonArray,
            results["SECTIONS"]?.jsonArray?.flatMap {
                it.jsonObject["items"]?.jsonArray ?: it.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
            }?.let { JsonArray(it) }
        )
        return candidates.firstOrNull { it.isNotEmpty() }?.map { it.jsonObject } ?: emptyList()
    }

    private fun parsePrivatePlaylistItem(obj: JsonObject): RawUserPlaylistSummary? {
        val id = obj["PLAYLIST_ID"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: return null
        val title = obj["TITLE"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: return null
        return RawUserPlaylistSummary(
            id = id,
            name = title,
            coverUrl = obj["PLAYLIST_PICTURE"]?.jsonPrimitive?.content,
            trackCount = obj["NB_SONG"]?.jsonPrimitive?.intOrNull ?: 0,
            platform = PlatformType.DEEZER,
            url = "https://www.deezer.com/playlist/$id"
        )
    }

    private suspend fun fetchPublicPlaylists(userId: String): Result<List<RawUserPlaylistSummary>> = runCatching {
        val response: String = httpClient.get("https://api.deezer.com/user/$userId/playlists").body()
        val data = json.parseToJsonElement(response).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        data.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RawUserPlaylistSummary(
                id = id,
                name = obj["title"]?.jsonPrimitive?.content ?: "Untitled Playlist",
                coverUrl = obj["picture_big"]?.jsonPrimitive?.content ?: obj["picture_medium"]?.jsonPrimitive?.content,
                trackCount = obj["nb_tracks"]?.jsonPrimitive?.intOrNull ?: 0,
                platform = PlatformType.DEEZER,
                url = obj["link"]?.jsonPrimitive?.content ?: "https://www.deezer.com/playlist/$id"
            )
        }
    }

    suspend fun parse(url: String, cookie: String? = null): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Deezer playlist ID in the link.")

        val responseText: String = httpClient.get("https://api.deezer.com/playlist/$playlistId") {
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()
        val root = json.parseToJsonElement(responseText).jsonObject

        val errorObj = root["error"]?.jsonObject
        if (errorObj != null) {
            val msg = errorObj["message"]?.jsonPrimitive?.content ?: "Deezer API error"
            throw IllegalStateException("Deezer playlist could not be loaded: $msg")
        }

        val title = root["title"]?.jsonPrimitive?.content ?: "Imported Deezer Playlist"
        val description = root["description"]?.jsonPrimitive?.content
        val coverUrl = root["picture_big"]?.jsonPrimitive?.content ?: root["picture_medium"]?.jsonPrimitive?.content

        val tracksObj = root["tracks"]?.jsonObject
        val data = tracksObj?.get("data")?.jsonArray ?: root["data"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

        val tracks = mutableListOf<RawImportTrack>()
        for (item in data) {
            val trackObj = item.jsonObject
            val name = trackObj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
            val artistName = trackObj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown Artist"
            val albumName = trackObj["album"]?.jsonObject?.get("title")?.jsonPrimitive?.content
            val durationSec = trackObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L

            tracks.add(
                RawImportTrack(
                    title = name,
                    artist = artistName,
                    album = albumName,
                    durationMs = durationSec * 1000L
                )
            )
        }

        check(tracks.isNotEmpty()) { "No tracks found in this Deezer playlist." }

        RawImportPlaylist(
            platform = PlatformType.DEEZER,
            title = title,
            description = description,
            coverUrl = coverUrl,
            tracks = tracks
        )
    }

    private companion object {
        const val GW_LIGHT_URL = "https://www.deezer.com/ajax/gw-light.php"
        const val TAG = "DeezerPlaylistParser"
    }
}
