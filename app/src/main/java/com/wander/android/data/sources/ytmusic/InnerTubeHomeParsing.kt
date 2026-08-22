package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.serialization.json.JsonObject

/**
 * YouTube Music's own home feed — the shelves it puts on its front page ("Listen again", "Mixed
 * for you", "Recommended radios", …), parsed straight out of `browse FEmusic_home`.
 *
 * This is the real recommender, personalised against the signed-in account, which is why the
 * shelves keep the titles YouTube gave them rather than being merged into one anonymous list.
 *
 * A shelf carries a mixture of cards. Only the ones that name a video are turned into tracks:
 * album and playlist cards point at a `browseEndpoint` and there is nothing playable behind them
 * until that browse happens, so they are dropped rather than rendered as tracks that do nothing.
 */
internal fun JsonObject.homeShelves(): List<RecommendedShelf> =
    renderers("musicCarouselShelfRenderer").mapNotNull { shelf ->
        val title = shelf.path(
            "header", "musicCarouselShelfBasicHeaderRenderer", "title"
        ).runText() ?: return@mapNotNull null

        // Both card shapes appear on the feed: two-row tiles on most shelves, list rows on the
        // "quick picks" one. Collected within this shelf, so a track never lands under another
        // shelf's heading.
        val tracks = shelf.renderers("musicTwoRowItemRenderer").mapNotNull(::parseTwoRowItem) +
            shelf.renderers("musicResponsiveListItemRenderer").mapNotNull(::parseResponsiveListItem)

        if (tracks.isEmpty()) return@mapNotNull null
        RecommendedShelf(
            // Derived from the title, not the position. The index used to be the id, which made
            // every shelf's identity change the moment YouTube reordered the feed — fine while
            // this was thrown away each launch, but it churns every row once the feed is cached.
            id = shelfId(title),
            title = title,
            tracks = tracks.distinctBy { it.id }
        )
    }

/**
 * A stable id for a shelf, from the name YouTube gave it.
 *
 * Lower-cased and stripped to word characters so trivial re-titling ("Listen Again" vs "Listen
 * again") does not read as a different shelf.
 */
private fun shelfId(title: String): String =
    "ytm_" + title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/**
 * One tile on the home feed.
 *
 * No duration: the tile does not carry one, and the alternative — a second request per card —
 * would cost a round-trip for a line of subtitle text. `TrackRow` and `HorizontalTrackCard`
 * already omit the duration when it is zero.
 */
private fun parseTwoRowItem(renderer: JsonObject): UnifiedTrack? {
    val videoId = renderer.path("navigationEndpoint", "watchEndpoint", "videoId").text()
        ?: return null

    val subtitle = InnerTubeSubtitle.of(renderer["subtitle"].path("runs")?.array())

    return UnifiedTrack(
        id = "$YTM_PREFIX$videoId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = subtitle.artist ?: "Unknown Artist",
        artworkUrl = renderer
            .path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail(),
        format = "audio/webm",
        bitRateKbps = 160
    )
}
