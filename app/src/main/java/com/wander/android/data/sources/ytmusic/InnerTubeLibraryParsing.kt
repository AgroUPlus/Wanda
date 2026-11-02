package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UNKNOWN_ARTIST
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The albums on one page of `FEmusic_liked_albums`, first page or continuation.
 *
 * The library answers in whichever layout the account last chose on the web: a grid of
 * `musicTwoRowItemRenderer` tiles (the default) or a list of `musicResponsiveListItemRenderer`
 * rows. Both are read, in document order within each, so neither layout comes back empty.
 */
internal fun JsonObject.libraryAlbums(): List<UnifiedAlbum> =
    (renderers("musicTwoRowItemRenderer").mapNotNull(::parseLibraryAlbumTile) +
        responsiveListItems().mapNotNull(::parseLibraryAlbum))
        .distinctBy { it.id }

/** A library album row: the same renderer shape as a track, but the endpoint points at a browse id. */
internal fun parseLibraryAlbum(renderer: JsonObject): UnifiedAlbum? {
    val columns = renderer["flexColumns"]?.array() ?: return null
    fun column(i: Int) = columns.getOrNull(i)
        .path("musicResponsiveListItemFlexColumnRenderer", "text")

    val browseId = renderer.path("navigationEndpoint", "browseEndpoint", "browseId").text()
        ?: return null
    val title = column(0).runText() ?: return null
    val subtitle = InnerTubeSubtitle.of(column(1).path("runs")?.array())

    return UnifiedAlbum(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        title = title,
        artist = subtitle.artist ?: UNKNOWN_ARTIST,
        artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" },
        year = subtitle.year,
        coverArtUrl = renderer.path("thumbnail", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

/**
 * A library grid tile. Only tiles that open an album page: the same grid can hold a "shuffle"
 * or "new" button tile, which points elsewhere and is not a record.
 */
internal fun parseLibraryAlbumTile(renderer: JsonObject): UnifiedAlbum? {
    val endpoint = renderer.path("navigationEndpoint", "browseEndpoint")
    if (endpoint.browsePageType() != MUSIC_ALBUM_PAGE) return null
    val browseId = endpoint.path("browseId").text()?.takeIf { it.isNotBlank() } ?: return null
    val subtitle = InnerTubeSubtitle.of(renderer["subtitle"].path("runs")?.array())

    return UnifiedAlbum(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = subtitle.artist ?: UNKNOWN_ARTIST,
        artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" },
        year = subtitle.year,
        coverArtUrl = renderer.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

/**
 * The playlists on one page of `FEmusic_liked_playlists`: the account's own, the ones it saved,
 * and YouTube's automatic ones (Liked Music, Episodes for later), each under the name YouTube
 * gives it in the account's language.
 *
 * Grid tiles and list rows both, like [libraryAlbums]. The grid opens with a "New playlist" tile
 * that creates rather than opens, which the page-type check leaves out.
 */
internal fun JsonObject.libraryPlaylists(): List<UnifiedPlaylist> =
    (renderers("musicTwoRowItemRenderer").mapNotNull { tile ->
        libraryPlaylist(
            endpoint = tile.path("navigationEndpoint", "browseEndpoint"),
            title = tile["title"].runText(),
            cover = tile.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
                .bestThumbnail()
        )
    } + responsiveListItems().mapNotNull { row ->
        libraryPlaylist(
            endpoint = row.path("navigationEndpoint", "browseEndpoint"),
            title = row["flexColumns"]?.array()?.firstOrNull()
                .path("musicResponsiveListItemFlexColumnRenderer", "text").runText(),
            cover = row.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
        )
    }).distinctBy { it.id }

/**
 * No track count: YouTube prints it as translated prose ("25 songs", "25 titres") in a subtitle
 * that may or may not carry it, and a number read off the wrong token would be stated as fact.
 * The playlist screen counts the tracks it loads instead.
 */
private fun libraryPlaylist(endpoint: JsonElement?, title: String?, cover: String?): UnifiedPlaylist? {
    if (endpoint.browsePageType() != MUSIC_PLAYLIST_PAGE) return null
    val browseId = endpoint.path("browseId").text()?.takeIf { it.isNotBlank() } ?: return null
    return UnifiedPlaylist(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        name = title?.takeIf { it.isNotBlank() } ?: return null,
        coverArtUrl = cover,
        // YouTube's own lists (Liked Music, Episodes for later) take no edits. A saved `PL…`
        // playlist of someone else's carries no owner here, so it reads as editable.
        isEditable = editablePlaylistId("$YTM_PREFIX$browseId") != null
    )
}

/** The page type a `browseEndpoint` says it opens, e.g. `MUSIC_PAGE_TYPE_ALBUM`. */
internal fun JsonElement?.browsePageType(): String? = path(
    "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig",
    "pageType"
).text()

internal const val MUSIC_ALBUM_PAGE = "MUSIC_PAGE_TYPE_ALBUM"
internal const val MUSIC_PLAYLIST_PAGE = "MUSIC_PAGE_TYPE_PLAYLIST"
