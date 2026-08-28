package com.wander.android.data.importer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleMusicPlaylistParser @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parse(url: String): Result<RawImportPlaylist> = runCatching {
        val trimmed = url.trim()
        val html: String = httpClient.get(trimmed) {
            header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
        }.body()

        // Extract title from og:title or <title>
        val ogTitle = Regex("""<meta\s+property=["']og:title["']\s+content=["'](.*?)["']""").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""<title>(.*?)</title>""").find(html)?.groupValues?.getOrNull(1)?.replace(" on Apple Music", "")
            ?: "Apple Music Playlist"

        val ogImage = Regex("""<meta\s+property=["']og:image["']\s+content=["'](.*?)["']""").find(html)?.groupValues?.getOrNull(1)

        val tracks = mutableListOf<RawImportTrack>()

        // 1. Try to find schema.org JSON-LD
        val ldJsonMatches = Regex("""<script\s+type=["']application/ld\+json["']>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).findAll(html)
        for (match in ldJsonMatches) {
            val content = match.groupValues.getOrNull(1)?.trim() ?: continue
            try {
                val element = json.parseToJsonElement(content)
                val trackList = if (element is kotlinx.serialization.json.JsonObject) {
                    element["track"]?.jsonArray
                } else null

                trackList?.forEach { item ->
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    val artistObj = obj["byArtist"]?.jsonObject
                    val artistName = artistObj?.get("name")?.jsonPrimitive?.content
                        ?: obj["byArtist"]?.jsonPrimitive?.content
                        ?: "Unknown Artist"

                    tracks.add(RawImportTrack(title = name, artist = artistName))
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback: Parse music:song or serialized structure if JSON-LD was absent
        if (tracks.isEmpty()) {
            val trackRowRegex = Regex("""data-testid=["']track-title["'][^>]*>(.*?)</div>.*?data-testid=["']track-artist["'][^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            for (match in trackRowRegex.findAll(html)) {
                val title = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                val artist = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                if (title.isNotBlank()) {
                    tracks.add(RawImportTrack(title = title, artist = artist))
                }
            }
        }

        if (tracks.isEmpty()) {
            throw IllegalStateException("Could not extract tracks from Apple Music page. Please ensure the playlist is public.")
        }

        RawImportPlaylist(
            platform = PlatformType.APPLE_MUSIC,
            title = ogTitle,
            coverUrl = ogImage,
            tracks = tracks
        )
    }
}
