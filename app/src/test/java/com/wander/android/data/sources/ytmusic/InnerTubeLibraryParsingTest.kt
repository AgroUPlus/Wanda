package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class InnerTubeLibraryParsingTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun tile(browseId: String, pageType: String, title: String) = """
        { "musicTwoRowItemRenderer": {
            "title": { "runs": [ { "text": "$title" } ] },
            "subtitle": { "runs": [ { "text": "Album" }, { "text": " • " },
              { "text": "Artist", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCa",
                "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST" } } } } } ] },
            "navigationEndpoint": { "browseEndpoint": { "browseId": "$browseId",
              "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                "pageType": "$pageType" } } } } } }
    """

    /** The library grid is the default layout, and was not read at all before. */
    @Test
    fun readsAlbumTilesOffTheLibraryGrid() {
        val page = json(
            """{ "gridRenderer": { "items": [
                 ${tile("MPREb_1", "MUSIC_PAGE_TYPE_ALBUM", "Record")},
                 ${tile("VLPL1", "MUSIC_PAGE_TYPE_PLAYLIST", "Not a record")} ] } }"""
        )
        val albums = page.libraryAlbums()
        assertEquals(listOf("ytm:MPREb_1"), albums.map { it.id })
        assertEquals("Artist", albums.single().artist)
        assertEquals("ytm:UCa", albums.single().artistId)
    }

    @Test
    fun listsLibraryPlaylistsButNotTheNewPlaylistButton() {
        val page = json(
            """{ "gridRenderer": { "items": [
                 { "musicTwoRowItemRenderer": { "title": { "runs": [ { "text": "New playlist" } ] },
                   "navigationEndpoint": { "createPlaylistEndpoint": {} } } },
                 ${tile("VLLM", "MUSIC_PAGE_TYPE_PLAYLIST", "Liked Music")},
                 ${tile("VLPLmine", "MUSIC_PAGE_TYPE_PLAYLIST", "Road trip")} ] } }"""
        )
        val playlists = page.libraryPlaylists()
        assertEquals(listOf("ytm:VLLM", "ytm:VLPLmine"), playlists.map { it.id })
        assertEquals("Road trip", playlists[1].name)
    }
}
