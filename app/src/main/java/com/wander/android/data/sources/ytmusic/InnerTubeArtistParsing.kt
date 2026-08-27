package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.ArtistSection
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import kotlinx.serialization.json.JsonObject

/**
 * A YouTube Music artist page, out of `browse <channelId>`.
 *
 * The shelves are taken as they come and keep YouTube's own titles. That is deliberate: which
 * shelves an artist has and what order they sit in is editorial — a band with three albums and no
 * singles has no "Singles" shelf, and inventing an empty one for the sake of a fixed layout would
 * state something untrue about them. So there is no fixed set of sections here, only whatever the
 * page actually carries.
 *
 * Two shelf shapes appear. `musicShelfRenderer` holds list rows and is what the songs shelf uses;
 * `musicCarouselShelfRenderer` holds tiles and is what everything else uses. A tile is a track when
 * it names a video and a record when it points at a browse id, which is the only reliable way to
 * tell the two apart — the titles are translated.
 */
internal fun JsonObject.artistPage(browseId: String): ArtistDetails? {
    val header = renderers("musicImmersiveHeaderRenderer").firstOrNull()
        ?: renderers("musicVisualHeaderRenderer").firstOrNull()
        ?: renderers("musicHeaderRenderer").firstOrNull()

    val name = header?.get("title").runText() ?: return null

    return ArtistDetails(
        id = "$YTM_PREFIX$browseId",
        name = name,
        imageUrl = header.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
            ?: header.path("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail")
                .bestThumbnail(),
        bio = artistBio(header) ?: descriptionShelfBio(),
        sections = artistSections()
    )
}

/**
 * What the page says about them.
 *
 * Two places carry it and neither is guaranteed: the immersive header's own `description`, and a
 * `musicDescriptionShelfRenderer` further down the page. Whichever exists is the bio; when neither
 * does, the artist has no bio and the screen says nothing rather than inventing a summary.
 */
private fun artistBio(header: JsonObject?): String? =
    header?.get("description").runText()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** The bio shelf, when the header did not carry one. */
private fun JsonObject.descriptionShelfBio(): String? =
    renderers("musicDescriptionShelfRenderer")
        .firstNotNullOfOrNull { it["description"].runText() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun JsonObject.artistSections(): List<ArtistSection> {
    val sections = mutableListOf<ArtistSection>()

    // List shelves first only because that is the order they appear in the response; both loops
    // preserve document order within themselves, which is what carries the page's arrangement.
    renderers("musicShelfRenderer").forEach { shelf ->
        val title = shelf.path("title").runText() ?: return@forEach
        val tracks = shelf.renderers("musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveListItem)
        if (tracks.isNotEmpty()) sections += ArtistTrackSection(title, tracks.distinctBy { it.id })
    }

    renderers("musicCarouselShelfRenderer").forEach { shelf ->
        val title = shelf.path(
            "header", "musicCarouselShelfBasicHeaderRenderer", "title"
        ).runText() ?: return@forEach

        val tiles = shelf.renderers("musicTwoRowItemRenderer")
        val albums = tiles.mapNotNull(::parseArtistPageAlbum)
        if (albums.isNotEmpty()) {
            sections += ArtistAlbumSection(title, albums.distinctBy { it.id })
            return@forEach
        }

        val tracks = shelf.renderers("musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveListItem)
        if (tracks.isNotEmpty()) sections += ArtistTrackSection(title, tracks.distinctBy { it.id })
    }

    return sections
}

/**
 * A record tile on an artist page.
 *
 * Only tiles that point at an album browse id: the same carousel also carries related-artist cards,
 * which point at a channel and have no tracks behind them, and rendering one as an album would give
 * the user a record that opens onto nothing.
 */
private fun parseArtistPageAlbum(renderer: JsonObject): UnifiedAlbum? {
    val pageType = renderer.path(
        "navigationEndpoint",
        "browseEndpoint",
        "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig",
        "pageType"
    ).text()
    if (pageType != ALBUM_PAGE_TYPE) return null

    val browseId = renderer.path("navigationEndpoint", "browseEndpoint", "browseId").text()
        ?: return null
    val subtitle = InnerTubeSubtitle.of(renderer["subtitle"].path("runs")?.array())

    return UnifiedAlbum(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = subtitle.artist ?: "Unknown Artist",
        artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" },
        coverArtUrl = renderer
            .path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

private const val ALBUM_PAGE_TYPE = "MUSIC_PAGE_TYPE_ALBUM"
