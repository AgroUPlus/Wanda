package com.wander.android.data.importer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerPlaylistParser @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""deezer\.com/(?:[a-zA-Z-]+/)?playlist/(\d+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    suspend fun parse(url: String): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Deezer playlist ID in the link.")

        val responseText: String = httpClient.get("https://api.deezer.com/playlist/$playlistId").body()
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
}
