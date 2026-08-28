package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.JsonObject

/** What an album's page says about itself: who it is by, and what it looks like. */
internal data class AlbumHeader(
    val artist: String?,
    val artistId: String?,
    /**
     * The sleeve.
     *
     * An album page prints its cover once, at the top; the track rows below carry no thumbnail of
     * their own, because on that screen they do not need one. Every track parsed off an album
     * therefore reached the library with no artwork — the same song had a cover when found by
     * search and none when opened from its own record.
     */
    val coverArtUrl: String?
)

/**
 * Who an album page says it is by.
 *
 * An album's track rows do not repeat the artist — the page has already said it once, at the top —
 * so every row parsed off one arrived with no artist at all and fell back to the literal string
 * "Unknown Artist". Opening *Ribcage* from Bella Poarch's page gave you a tracklist credited to
 * nobody, on a page you reached from her name.
 *
 * The credit lives in the header, and YouTube has two of them in circulation: the current
 * `musicResponsiveHeaderRenderer`, which puts the artist in `straplineTextOne`, and the older
 * `musicDetailHeaderRenderer`, which puts it in `subtitle` alongside the year and the track count.
 * Both are swept for, newest first.
 *
 * Reuses [InnerTubeSubtitle] rather than reading a run by position, for exactly the reason that
 * class exists: position is not stable across surfaces, and only a link to an artist page reliably
 * identifies an artist. Null when neither header is present or neither names anybody — in which
 * case the caller keeps whatever the rows themselves managed to say.
 */
internal fun JsonObject.albumHeader(): AlbumHeader? =
    header("musicResponsiveHeaderRenderer", "straplineTextOne")
        ?: header("musicDetailHeaderRenderer", "subtitle")

private fun JsonObject.header(renderer: String, field: String): AlbumHeader? =
    renderers(renderer).firstNotNullOfOrNull { header ->
        val subtitle = InnerTubeSubtitle.of(header[field].path("runs")?.array())
        // The linked run only. The positional fallback would happily return "2021" or
        // "1.2M plays" from a subtitle that never named an artist, which is a worse answer than
        // admitting we do not know — see `InnerTubeSubtitle.linkedArtist`.
        val cover = header.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
            ?: header.path(
                "thumbnail",
                "croppedSquareThumbnailRenderer",
                "thumbnail"
            ).bestThumbnail()
        val artist = subtitle.linkedArtist?.takeIf { it.isNotBlank() }
        // A header that names nobody and shows nothing is not a header worth returning, and
        // returning it would stop the older renderer below from being tried.
        if (artist == null && cover == null) return@firstNotNullOfOrNull null
        AlbumHeader(
            artist = artist,
            artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" },
            coverArtUrl = cover
        )
    }
