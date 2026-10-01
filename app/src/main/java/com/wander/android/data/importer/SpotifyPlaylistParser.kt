package com.wander.android.data.importer

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a public Spotify playlist from its embed page (`/embed/playlist/{id}`), the page Spotify
 * publishes for putting a playlist on other websites. Its server-rendered `__NEXT_DATA__` JSON
 * already holds the track list, so no token, login or developer app is involved.
 *
 * Spotify's web-player token endpoint, which this parser used before, now refuses every client
 * that isn't its own player, and its official API needs a Premium developer app of the user's own.
 *
 * What the embed gives is what the page shows: title, artists and duration per track, no album, and
 * only for a playlist that anyone with the link can open — a private one answers with an error.
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

    suspend fun parse(url: String): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid Spotify playlist ID in the link.")

        val response = httpClient.get("$EMBED_URL/$playlistId") {
            header("User-Agent", IMPORT_WEB_USER_AGENT)
        }
        check(response.status.isSuccess()) {
            "Spotify could not open this playlist (HTTP ${response.status.value}). " +
                "Only playlists anyone with the link can open can be imported."
        }
        parseEmbedPage(response.bodyAsText())
    }

    private fun parseEmbedPage(html: String): RawImportPlaylist {
        val script = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Spotify's playlist page had no playlist data in it.")
        val entity = json.parseToJsonElement(script)
            .child("props").child("pageProps").child("state").child("data").child("entity")
            ?.jsonObject
            ?: throw IllegalStateException("Spotify's playlist page had no playlist data in it.")

        val tracks = entity["trackList"]?.jsonArray.orEmpty().mapNotNull { item ->
            val track = item.jsonObject
            val title = track["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RawImportTrack(
                title = title,
                // The embed separates artists with a non-breaking space after the comma.
                artist = track["subtitle"]?.jsonPrimitive?.content?.replace(' ', ' ') ?: "Unknown Artist",
                album = null,
                durationMs = track["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }
        check(tracks.isNotEmpty()) { "No tracks could be found in this Spotify playlist." }

        return RawImportPlaylist(
            platform = PlatformType.SPOTIFY,
            title = (entity["title"] ?: entity["name"])?.jsonPrimitive?.content ?: "Imported Spotify Playlist",
            description = null,
            coverUrl = entity["coverArt"].child("sources")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.content,
            tracks = tracks
        )
    }

    private fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

    private companion object {
        const val EMBED_URL = "https://open.spotify.com/embed/playlist"
        val NEXT_DATA_REGEX = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
    }
}
