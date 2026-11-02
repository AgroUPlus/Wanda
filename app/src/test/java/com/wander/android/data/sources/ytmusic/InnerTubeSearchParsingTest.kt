package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class InnerTubeSearchParsingTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun endpoint(browseId: String, pageType: String) = """
        { "browseEndpoint": { "browseId": "$browseId",
          "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
            "pageType": "$pageType" } } } }
    """

    private fun row(title: String, subtitleRuns: String, browseId: String, pageType: String) = """
        { "musicResponsiveListItemRenderer": {
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "$title" } ] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ $subtitleRuns ] } } } ],
            "navigationEndpoint": ${endpoint(browseId, pageType)} } }
    """

    @Test
    fun readsAlbumRowsAndSkipsOtherKinds() {
        val artistRun = """{ "text": "Radiohead", "navigationEndpoint": ${endpoint("UCr", "MUSIC_PAGE_TYPE_ARTIST")} }"""
        val albumLine = """{ "text": "Album" }, { "text": " • " }, $artistRun, { "text": " • " }, { "text": "1997" }"""
        val album = row("OK Computer", albumLine, "MPREb_ok", "MUSIC_PAGE_TYPE_ALBUM")
        val artist = row("Radiohead", """{ "text": "Artist" }""", "UCr", "MUSIC_PAGE_TYPE_ARTIST")
        val page = json("""{ "contents": [ $album, $artist ] }""")
        val albums = page.albumSearchResults()
        assertEquals(listOf("ytm:MPREb_ok"), albums.map { it.id })
        assertEquals("Radiohead", albums.single().artist)
        assertEquals(1997, albums.single().year)
    }

    @Test
    fun readsArtistRows() {
        val artistLine = """{ "text": "Artist" }, { "text": " • " }, { "text": "5.2M subscribers" }"""
        val artist = row("Radiohead", artistLine, "UCr", "MUSIC_PAGE_TYPE_ARTIST")
        val album = row("OK Computer", """{ "text": "Album" }""", "MPREb_ok", "MUSIC_PAGE_TYPE_ALBUM")
        val page = json("""{ "contents": [ $artist, $album ] }""")
        val artists = page.artistSearchResults()
        assertEquals(listOf("ytm:UCr"), artists.map { it.id })
        assertEquals("Radiohead", artists.single().name)
    }
}
