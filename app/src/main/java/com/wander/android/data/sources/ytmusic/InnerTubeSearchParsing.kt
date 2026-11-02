package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedArtist
import kotlinx.serialization.json.JsonObject

/**
 * The records on an albums-filtered search. The rows are the library-list shape — title, then an
 * `Album • Artist • 2020` line — so they go through the same parser, held to rows that open an
 * album page: a filtered search can still lead with a "top result" card of another kind.
 */
internal fun JsonObject.albumSearchResults(): List<UnifiedAlbum> =
    responsiveListItems()
        .filter { it.path("navigationEndpoint", "browseEndpoint").browsePageType() == MUSIC_ALBUM_PAGE }
        .mapNotNull(::parseLibraryAlbum)
        .distinctBy { it.id }

/**
 * The artists on an artists-filtered search: a name, a portrait, and the channel id their page
 * opens with. The subscriber count in the second column is not a field anything here has.
 */
internal fun JsonObject.artistSearchResults(): List<UnifiedArtist> =
    responsiveListItems().mapNotNull { row ->
        val endpoint = row.path("navigationEndpoint", "browseEndpoint")
        if (endpoint.browsePageType() != MUSIC_ARTIST_PAGE) return@mapNotNull null
        val channelId = endpoint.path("browseId").text()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val name = row["flexColumns"]?.array()?.firstOrNull()
            .path("musicResponsiveListItemFlexColumnRenderer", "text").runText()
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        UnifiedArtist(
            id = "$YTM_PREFIX$channelId",
            source = SourceType.YTMUSIC,
            name = name,
            coverArtUrl = row.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
        )
    }.distinctBy { it.id }

internal const val MUSIC_ARTIST_PAGE = "MUSIC_PAGE_TYPE_ARTIST"
