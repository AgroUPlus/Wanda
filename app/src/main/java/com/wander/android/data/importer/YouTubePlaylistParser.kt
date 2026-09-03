package com.wander.android.data.importer

import com.wander.android.data.sources.ytmusic.InnerTubeClient
import com.wander.android.data.sources.ytmusic.array
import com.wander.android.data.sources.ytmusic.bestThumbnail
import com.wander.android.data.sources.ytmusic.parseResponsiveListItem
import com.wander.android.data.sources.ytmusic.path
import com.wander.android.data.sources.ytmusic.renderers
import com.wander.android.data.sources.ytmusic.responsiveListItems
import com.wander.android.data.sources.ytmusic.runText
import com.wander.android.data.sources.ytmusic.text
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubePlaylistParser @Inject constructor(
    private val innerTubeClient: InnerTubeClient
) {
    fun extractPlaylistId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    suspend fun parse(url: String): Result<RawImportPlaylist> = runCatching {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("Could not find a valid YouTube playlist ID.")

        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val root = innerTubeClient.browse(browseId).getOrThrow()

        val parsedTracks = root.responsiveListItems()
            .mapNotNull(::parseResponsiveListItem)

        check(parsedTracks.isNotEmpty()) { "No tracks found in this YouTube playlist." }

        val rawTracks = parsedTracks.map { track ->
            RawImportTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
        }

        val onlineTitle = root.path("header", "musicDetailHeaderRenderer", "title").runText()
            ?: root.path("header", "musicResponsiveHeaderRenderer", "title").runText()
            ?: root.path("header", "musicEditablePlaylistDetailHeaderRenderer", "header", "musicDetailHeaderRenderer", "title").runText()
            ?: root.path("header", "musicEditablePlaylistDetailHeaderRenderer", "header", "musicResponsiveHeaderRenderer", "title").runText()
            ?: root.path("contents", "twoColumnBrowseResultsRenderer", "tabs")?.array()?.firstOrNull()?.path("tabRenderer", "content", "sectionListRenderer", "contents")?.array()?.firstOrNull()?.path("musicResponsiveHeaderRenderer", "title").runText()
            ?: if (playlistId == "LM") "Liked Music" else "YouTube Music Playlist"

        val onlineCover = root.path("header", "musicDetailHeaderRenderer", "thumbnail", "croppedSquareThumbnailRenderer", "thumbnail").bestThumbnail()
            ?: root.path("header", "musicResponsiveHeaderRenderer", "thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
            ?: root.path("header", "musicEditablePlaylistDetailHeaderRenderer", "header", "musicDetailHeaderRenderer", "thumbnail", "croppedSquareThumbnailRenderer", "thumbnail").bestThumbnail()
            ?: parsedTracks.firstOrNull()?.artworkUrl

        RawImportPlaylist(
            platform = PlatformType.YOUTUBE,
            title = onlineTitle,
            description = null,
            coverUrl = onlineCover,
            tracks = rawTracks
        )
    }

    suspend fun fetchUserPlaylists(): Result<List<RawUserPlaylistSummary>> = runCatching {
        val root = innerTubeClient.browse("FEmusic_liked_playlists").getOrThrow()

        val list = mutableListOf<RawUserPlaylistSummary>()
        list.add(
            RawUserPlaylistSummary(
                id = "LM",
                name = "Liked Music",
                description = "Your liked songs on YouTube Music",
                platform = PlatformType.YOUTUBE,
                url = "https://music.youtube.com/playlist?list=LM"
            )
        )

        // Two row items (library grid)
        val twoRowItems = root.renderers("musicTwoRowItemRenderer")
        for (item in twoRowItems) {
            val browseId = item.path("navigationEndpoint", "browseEndpoint", "browseId").text() ?: continue
            val title = item.path("title").runText() ?: continue
            val subtitle = item.path("subtitle").runText()
            val thumbnail = item.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
                ?: item.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
                ?: item.path("thumbnailRenderer").bestThumbnail()
                ?: item.path("thumbnail").bestThumbnail()
                ?: item.bestThumbnail()
            val cleanId = browseId.removePrefix("VL")

            if (cleanId != "LM" && cleanId != "FEmusic_liked_videos") {
                list.add(
                    RawUserPlaylistSummary(
                        id = cleanId,
                        name = title,
                        description = subtitle,
                        coverUrl = thumbnail,
                        platform = PlatformType.YOUTUBE,
                        url = "https://music.youtube.com/playlist?list=$cleanId"
                    )
                )
            }
        }

        // List items
        val listItems = root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        for (item in listItems) {
            val cleanId = item.id.removePrefix("ytm:").removePrefix("VL")
            if (list.none { it.id == cleanId }) {
                list.add(
                    RawUserPlaylistSummary(
                        id = cleanId,
                        name = item.title,
                        description = item.artist,
                        coverUrl = item.artworkUrl,
                        platform = PlatformType.YOUTUBE,
                        url = "https://music.youtube.com/playlist?list=$cleanId"
                    )
                )
            }
        }
        list
    }
}
