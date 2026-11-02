package com.wander.android.data.sources.deezer

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The gateway's own shapes — upper-case keys, numbers sent as strings, covers as an image hash —
 * read into Wanda's models with the same `deezer:` ids the public API's tracks carry, so a song
 * from the account's favourites streams, likes and deduplicates like one from search.
 */
internal object DeezerGwParsing {

    fun track(json: JsonObject): UnifiedTrack? {
        val id = json.text("SNG_ID") ?: return null
        val title = json.text("SNG_TITLE") ?: return null
        val version = json.text("VERSION")
        return UnifiedTrack(
            id = "$PREFIX$id",
            source = SourceType.DEEZER,
            title = if (version.isNullOrBlank()) title else "$title $version",
            artist = json.text("ART_NAME") ?: "Unknown Artist",
            album = json.text("ALB_TITLE"),
            albumId = json.text("ALB_ID")?.let { "$PREFIX$it" },
            artistId = json.text("ART_ID")?.let { "$PREFIX$it" },
            durationMs = (json.text("DURATION")?.toLongOrNull() ?: 0L) * 1000L,
            artworkUrl = image("cover", json.text("ALB_PICTURE")),
            trackNumber = json.text("TRACK_NUMBER")?.toIntOrNull(),
            discNumber = json.text("DISK_NUMBER")?.toIntOrNull()
        )
    }

    fun album(json: JsonObject): UnifiedAlbum? {
        val id = json.text("ALB_ID") ?: return null
        return UnifiedAlbum(
            id = "$PREFIX$id",
            source = SourceType.DEEZER,
            title = json.text("ALB_TITLE") ?: return null,
            artist = json.text("ART_NAME") ?: "Unknown Artist",
            artistId = json.text("ART_ID")?.let { "$PREFIX$it" },
            coverArtUrl = image("cover", json.text("ALB_PICTURE")),
            songCount = json.text("NUMBER_TRACK")?.toIntOrNull() ?: 0,
            year = (json.text("DIGITAL_RELEASE_DATE") ?: json.text("PHYSICAL_RELEASE_DATE"))
                ?.take(YEAR_DIGITS)?.toIntOrNull()
        )
    }

    /** [userId] is the signed-in account's: only its own playlists can be edited. */
    fun playlist(json: JsonObject, userId: String?): UnifiedPlaylist? {
        val id = json.text("PLAYLIST_ID") ?: return null
        return UnifiedPlaylist(
            id = "$PREFIX$id",
            source = SourceType.DEEZER,
            name = json.text("TITLE") ?: return null,
            coverArtUrl = image(json.text("PICTURE_TYPE") ?: "playlist", json.text("PLAYLIST_PICTURE")),
            songCount = json.text("NB_SONG")?.toIntOrNull() ?: 0,
            isPublic = json.text("STATUS") == PUBLIC_STATUS,
            isEditable = userId != null && json.text("PARENT_USER_ID") == userId
        )
    }

    fun <T> list(data: JsonElement?, parse: (JsonObject) -> T?): List<T> =
        (data as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(parse) }

    /** Deezer's CDN address for an image hash, at the size the public API's `_big` covers are. */
    private fun image(type: String, hash: String?): String? =
        hash?.takeIf { it.isNotBlank() }?.let { "$IMAGE_CDN/$type/$it/500x500-000000-80-0-0.jpg" }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private const val PREFIX = "deezer:"
    private const val IMAGE_CDN = "https://e-cdns-images.dzcdn.net/images"
    private const val PUBLIC_STATUS = "0"
    private const val YEAR_DIGITS = 4
}
