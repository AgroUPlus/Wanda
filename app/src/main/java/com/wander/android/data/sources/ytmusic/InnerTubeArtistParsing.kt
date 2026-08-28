package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.ArtistSection
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.RelatedArtist
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
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
        sections = artistSections(artist = name, artistId = "$YTM_PREFIX$browseId"),
        related = relatedArtists()
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

private fun JsonObject.artistSections(artist: String, artistId: String): List<ArtistSection> {
    val sections = mutableListOf<ArtistSection>()

    // List shelves first only because that is the order they appear in the response; both loops
    // preserve document order within themselves, which is what carries the page's arrangement.
    renderers("musicShelfRenderer").forEach { shelf ->
        val title = shelf.path("title").runText() ?: return@forEach
        val tracks = shelf.renderers("musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveListItem)
            .map { it.creditedTo(artist, artistId) }
        if (tracks.isNotEmpty()) sections += ArtistTrackSection(title, tracks.distinctBy { it.id })
    }

    renderers("musicCarouselShelfRenderer").forEach { shelf ->
        val title = shelf.path(
            "header", "musicCarouselShelfBasicHeaderRenderer", "title"
        ).runText() ?: return@forEach

        val tiles = shelf.renderers("musicTwoRowItemRenderer")
        val albums = tiles.mapNotNull { parseArtistPageAlbum(it, artist, artistId) }
        if (albums.isNotEmpty()) {
            val more = shelf.moreEndpoint()
            sections += ArtistAlbumSection(
                title = title,
                albums = albums.distinctBy { it.id },
                moreBrowseId = more?.first,
                moreParams = more?.second
            )
            return@forEach
        }

        val tracks = shelf.renderers("musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveListItem)
            .map { it.creditedTo(artist, artistId) }
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
private fun parseArtistPageAlbum(
    renderer: JsonObject,
    /** Whose page this tile was found on — see [InnerTubeSubtitle.linkedArtist]. */
    fallbackArtist: String,
    fallbackArtistId: String
): UnifiedAlbum? {
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

    // Only a run that links to an artist page is a credit. On an artist's own page the tile's
    // subtitle is `Album • 2023` with nothing linked, and taking its first token as the artist is
    // what put "More from 2023" at the bottom of those albums. The page's own artist is the
    // correct answer there, and it is already known.
    return UnifiedAlbum(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = subtitle.linkedArtist ?: fallbackArtist,
        artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" } ?: fallbackArtistId,
        year = subtitle.year,
        coverArtUrl = renderer
            .path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

private const val ALBUM_PAGE_TYPE = "MUSIC_PAGE_TYPE_ALBUM"

/**
 * The shelf's "more" button, as a browse id and its params.
 *
 * Both halves are required: the browse id alone lands on a generic page, and it is the params blob
 * that says *which* of the artist's shelves to expand. A shelf without the button is a shelf that
 * was already complete.
 */
private fun JsonObject.moreEndpoint(): Pair<String, String?>? {
    val endpoint = path(
        "header",
        "musicCarouselShelfBasicHeaderRenderer",
        "moreContentButton",
        "buttonRenderer",
        "navigationEndpoint",
        "browseEndpoint"
    ) ?: return null
    val browseId = endpoint.path("browseId").text()?.takeIf { it.isNotBlank() } ?: return null
    // Namespaced like every other id this source hands out, so the repository can tell which
    // backend a shelf belongs to without being told separately.
    return "$YTM_PREFIX$browseId" to endpoint.path("params").text()
}

/**
 * The tiles on an expanded shelf page.
 *
 * Same renderer, same parser as the carousel it came from — the "more" page differs only in that
 * it lays them out as a grid and holds all of them.
 */
internal fun JsonObject.artistAlbumGrid(artist: String, artistId: String): List<UnifiedAlbum> =
    renderers("musicTwoRowItemRenderer")
        .mapNotNull { parseArtistPageAlbum(it, artist, artistId) }
        .distinctBy { it.id }

/**
 * The "Fans might also like" shelf.
 *
 * These tiles ride in the same carousels as the record tiles and were previously discarded
 * wholesale by [parseArtistPageAlbum]'s page-type filter — correctly, since rendering a channel as
 * an album gives the user a record that opens onto nothing. Collected separately here instead, so
 * the suggestion survives without being mistaken for a release.
 */
private fun JsonObject.relatedArtists(): List<RelatedArtist> =
    renderers("musicTwoRowItemRenderer")
        .mapNotNull(::parseRelatedArtist)
        .distinctBy { it.id }

private fun parseRelatedArtist(renderer: JsonObject): RelatedArtist? {
    val pageType = renderer.path(
        "navigationEndpoint",
        "browseEndpoint",
        "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig",
        "pageType"
    ).text()
    if (pageType != ARTIST_PAGE_TYPE) return null

    val browseId = renderer.path("navigationEndpoint", "browseEndpoint", "browseId").text()
        ?: return null

    return RelatedArtist(
        id = "$YTM_PREFIX$browseId",
        name = renderer["title"].runText() ?: return null,
        imageUrl = renderer
            .path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

private const val ARTIST_PAGE_TYPE = "MUSIC_PAGE_TYPE_ARTIST"

/**
 * Credits a row to the artist whose page it was found on, when the row does not name one itself.
 *
 * An artist's own page does not repeat their name on every song — you are already looking at it —
 * so rows parsed off a "Songs" shelf arrived credited to nobody and fell back to the literal
 * "Unknown Artist". One library had thirty-seven Katy Perry tracks filed that way, every one of
 * them found on her own page.
 *
 * Exactly the rule the album pages use, for exactly the same reason: only rows that could not
 * speak for themselves are filled in, so a featured artist named on their own row is left alone.
 */
private fun UnifiedTrack.creditedTo(artist: String, artistId: String): UnifiedTrack =
    if (this.artist != UNKNOWN_ARTIST) this
    else copy(artist = artist, artistId = this.artistId ?: artistId)
