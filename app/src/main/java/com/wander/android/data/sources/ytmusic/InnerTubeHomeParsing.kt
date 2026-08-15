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
    renderers("musicCarouselShelfRenderer").mapIndexedNotNull { index, shelf ->
        val title = shelf.path(
            "header", "musicCarouselShelfBasicHeaderRenderer", "title"
        ).runText() ?: return@mapIndexedNotNull null

        // Both card shapes appear on the feed: two-row tiles on most shelves, list rows on the
        // "quick picks" one. Collected within this shelf, so a track never lands under another
        // shelf's heading.
        val tracks = shelf.renderers("musicTwoRowItemRenderer").mapNotNull(::parseTwoRowItem) +
            shelf.renderers("musicResponsiveListItemRenderer").mapNotNull(::parseResponsiveListItem)

        if (tracks.isEmpty()) return@mapIndexedNotNull null
        RecommendedShelf(
            // The index is part of the id because YouTube reuses shelf titles between feeds, and
            // Home keys its lazy items on it.
            id = "ytm_home_$index",
            title = title,
            tracks = tracks.distinctBy { it.id }
        )
    }

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
