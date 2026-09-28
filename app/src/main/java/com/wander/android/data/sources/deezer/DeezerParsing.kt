package com.wander.android.data.sources.deezer

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedArtist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses Deezer public REST API JSON responses into Wanda's unified models.
 */
internal object DeezerParsing {

    fun parseTrack(json: JsonObject): UnifiedTrack? {
        val id = json["id"]?.jsonPrimitive?.content ?: return null
        val title = json["title"]?.jsonPrimitive?.content ?: return null

        val artistObj = json["artist"]?.jsonObject
        val artistName = artistObj?.get("name")?.jsonPrimitive?.content ?: "Unknown Artist"
        val artistId = artistObj?.get("id")?.jsonPrimitive?.content

        val albumObj = json["album"]?.jsonObject
        val albumTitle = albumObj?.get("title")?.jsonPrimitive?.content
        val albumId = albumObj?.get("id")?.jsonPrimitive?.content

        val durationSec = json["duration"]?.jsonPrimitive?.longOrNull ?: 0L
        val artwork = albumObj?.get("cover_big")?.jsonPrimitive?.content
            ?: albumObj?.get("cover_medium")?.jsonPrimitive?.content
            ?: artistObj?.get("picture_medium")?.jsonPrimitive?.content

        val trackPos = json["track_position"]?.jsonPrimitive?.intOrNull
        val discNum = json["disk_number"]?.jsonPrimitive?.intOrNull
        val releaseDate = json["release_date"]?.jsonPrimitive?.content
        val year = releaseDate?.take(4)?.toIntOrNull()

        return UnifiedTrack(
            id = "deezer:$id",
            source = SourceType.DEEZER,
            title = title,
            artist = artistName,
            album = albumTitle,
            albumId = albumId?.let { "deezer:$it" },
            artistId = artistId?.let { "deezer:$it" },
            durationMs = durationSec * 1000L,
            artworkUrl = artwork,
            trackNumber = trackPos,
            discNumber = discNum,
            year = year
        )
    }

    fun parseTrackList(dataArray: JsonArray): List<UnifiedTrack> =
        dataArray.mapNotNull { it.jsonObject.let(::parseTrack) }

    fun parseAlbum(json: JsonObject): UnifiedAlbum? {
        val id = json["id"]?.jsonPrimitive?.content ?: return null
        val title = json["title"]?.jsonPrimitive?.content ?: return null

        val artistObj = json["artist"]?.jsonObject
        val artistName = artistObj?.get("name")?.jsonPrimitive?.content ?: "Unknown Artist"
        val artistId = artistObj?.get("id")?.jsonPrimitive?.content

        val cover = json["cover_big"]?.jsonPrimitive?.content
            ?: json["cover_medium"]?.jsonPrimitive?.content

        val trackCount = json["nb_tracks"]?.jsonPrimitive?.intOrNull ?: 0
        val durationSec = json["duration"]?.jsonPrimitive?.longOrNull ?: 0L
        val releaseDate = json["release_date"]?.jsonPrimitive?.content
        val year = releaseDate?.take(4)?.toIntOrNull()

        return UnifiedAlbum(
            id = "deezer:$id",
            source = SourceType.DEEZER,
            title = title,
            artist = artistName,
            artistId = artistId?.let { "deezer:$it" },
            coverArtUrl = cover,
            songCount = trackCount,
            durationMs = durationSec * 1000L,
            year = year
        )
    }

    fun parseAlbumList(dataArray: JsonArray): List<UnifiedAlbum> =
        dataArray.mapNotNull { it.jsonObject.let(::parseAlbum) }

    fun parseArtist(json: JsonObject): UnifiedArtist? {
        val id = json["id"]?.jsonPrimitive?.content ?: return null
        val name = json["name"]?.jsonPrimitive?.content ?: return null
        val picture = json["picture_big"]?.jsonPrimitive?.content
            ?: json["picture_medium"]?.jsonPrimitive?.content
        val nbAlbums = json["nb_album"]?.jsonPrimitive?.intOrNull ?: 0

        return UnifiedArtist(
            id = "deezer:$id",
            source = SourceType.DEEZER,
            name = name,
            coverArtUrl = picture,
            albumCount = nbAlbums
        )
    }

    fun parseArtistDetails(
        artistJson: JsonObject,
        topTracks: List<UnifiedTrack>,
        albums: List<UnifiedAlbum>
    ): ArtistDetails {
        val id = artistJson["id"]?.jsonPrimitive?.content.orEmpty()
        val name = artistJson["name"]?.jsonPrimitive?.content.orEmpty()
        val image = artistJson["picture_big"]?.jsonPrimitive?.content
            ?: artistJson["picture_medium"]?.jsonPrimitive?.content

        val sections = mutableListOf<com.wander.android.data.model.ArtistSection>()
        if (topTracks.isNotEmpty()) {
            sections.add(ArtistTrackSection(title = "Top tracks", tracks = topTracks))
        }
        if (albums.isNotEmpty()) {
            sections.add(ArtistAlbumSection(title = "Albums", albums = albums))
        }

        return ArtistDetails(
            id = "deezer:$id",
            name = name,
            imageUrl = image,
            sections = sections
        )
    }
}
