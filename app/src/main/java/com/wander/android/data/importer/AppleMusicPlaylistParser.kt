package com.wander.android.data.importer

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    suspend fun parse(url: String, cookie: String? = null): Result<RawImportPlaylist> = runCatching {
        val html: String = httpClient.get(url.trim()) {
            header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            if (!cookie.isNullOrBlank()) {
                header("Cookie", cookie)
            }
        }.body()

        // schema.org JSON-LD first; the serialized track rows only if it carried no tracklist.
        val tracks = jsonLdTracks(html).ifEmpty { trackRowTracks(html) }
        check(tracks.isNotEmpty()) {
            "Could not extract tracks from Apple Music page. Please ensure the playlist is public."
        }

        RawImportPlaylist(
            platform = PlatformType.APPLE_MUSIC,
            title = pageTitle(html),
            coverUrl = OG_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1),
            tracks = tracks
        )
    }

    /** Title from og:title or <title>. */
    private fun pageTitle(html: String): String =
        OG_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?: TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.replace(" on Apple Music", "")
            ?: "Apple Music Playlist"

    private fun jsonLdTracks(html: String): List<RawImportTrack> {
        val tracks = mutableListOf<RawImportTrack>()
        for (match in JSON_LD_REGEX.findAll(html)) {
            val content = match.groupValues.getOrNull(1)?.trim() ?: continue
            try {
                collectJsonLdTracks(json.parseToJsonElement(content), tracks)
            } catch (e: Exception) {
                // One malformed JSON-LD block is not a failed import. Apple ships several of these
                // scripts per page and only some carry a tracklist; the regex fallback picks
                // the page up if none of them parsed.
                Log.d(TAG, "Skipping unparseable JSON-LD block", e)
            }
        }
        return tracks
    }

    private fun collectJsonLdTracks(element: JsonElement, into: MutableList<RawImportTrack>) {
        val trackList = (element as? JsonObject)?.get("track")?.jsonArray ?: return
        trackList.forEach { item ->
            val obj = item.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
            val artistName = obj["byArtist"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                ?: obj["byArtist"]?.jsonPrimitive?.content
                ?: "Unknown Artist"
            into.add(RawImportTrack(title = name, artist = artistName))
        }
    }

    private fun trackRowTracks(html: String): List<RawImportTrack> =
        TRACK_ROW_REGEX.findAll(html).mapNotNull { match ->
            val title = match.groupValues[1].replace(HTML_TAG_REGEX, "").trim()
            val artist = match.groupValues[2].replace(HTML_TAG_REGEX, "").trim()
            if (title.isNotBlank()) RawImportTrack(title = title, artist = artist) else null
        }.toList()

    private companion object {
        const val TAG = "AppleMusicParser"
        val OG_TITLE_REGEX = Regex("""<meta\s+property=["']og:title["']\s+content=["'](.*?)["']""")
        val OG_IMAGE_REGEX = Regex("""<meta\s+property=["']og:image["']\s+content=["'](.*?)["']""")
        val TITLE_REGEX = Regex("""<title>(.*?)</title>""")
        val JSON_LD_REGEX = Regex("""<script\s+type=["']application/ld\+json["']>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val TRACK_ROW_REGEX = Regex(
            """data-testid=["']track-title["'][^>]*>(.*?)</div>.*?data-testid=["']track-artist["'][^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
