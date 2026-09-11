package com.wander.android

import com.wander.android.data.sources.ytmusic.YouTubeEntityKind
import com.wander.android.data.sources.ytmusic.youTubeEntity
import com.wander.android.data.sources.ytmusic.youTubeVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which YouTube URLs name a record, a playlist or an artist.
 *
 * The companion to [YouTubeLinkParsingTest], and it exists for the same reason: the manifest claims
 * every `youtube.com` URL with no path restriction, so a form missing from this list is one Wanda
 * offers itself for in the share chooser and then refuses. Albums, playlists and artist pages were
 * all in that state — advertised, then answered with "that link isn't a track Wanda can play".
 *
 * The one rule worth stating twice: a video always wins. `watch?v=…&list=…` is a song inside a
 * playlist, and the song is what was tapped.
 */
class YouTubeEntityLinkTest {

    /** Parses a URL without `Uri`, which has no behaviour off-device. */
    private fun parse(url: String) = parts(url).let { (host, path, query) ->
        youTubeEntity(
            host = host,
            pathSegments = path.split('/').filter { it.isNotEmpty() },
            queryParam = { query[it] }
        )
    }

    private fun videoId(url: String) = parts(url).let { (host, path, query) ->
        youTubeVideoId(
            host = host,
            pathSegments = path.split('/').filter { it.isNotEmpty() },
            videoQueryParam = { query[it] }
        )
    }

    private fun parts(url: String): Triple<String, String, Map<String, String>> {
        val afterScheme = url.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?')
        val path = afterScheme.substringAfter('/', "").substringBefore('?')
        val query = afterScheme.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        return Triple(host, path, query)
    }

    @Test
    fun `a playlist link names a playlist`() {
        val entity = parse("https://www.youtube.com/playlist?list=PLabcdefgh123")
        assertEquals(YouTubeEntityKind.PLAYLIST, entity?.kind)
        assertEquals("PLabcdefgh123", entity?.id)
    }

    @Test
    fun `a YouTube Music album browse id names an album`() {
        val entity = parse("https://music.youtube.com/browse/MPREb_abc123DEF")
        assertEquals(YouTubeEntityKind.ALBUM, entity?.kind)
        assertEquals("MPREb_abc123DEF", entity?.id)
    }

    @Test
    fun `a channel link names an artist`() {
        val entity = parse("https://www.youtube.com/channel/UCabc123def456ghi789jkl")
        assertEquals(YouTubeEntityKind.ARTIST, entity?.kind)
        assertEquals("UCabc123def456ghi789jkl", entity?.id)
    }

    @Test
    fun `an artist browse id names an artist`() {
        val entity = parse("https://music.youtube.com/browse/UCabc123def456ghi789jkl")
        assertEquals(YouTubeEntityKind.ARTIST, entity?.kind)
    }

    /**
     * The case both parsers see. Neither may guess: the entity parser declines it outright rather
     * than relying on call order, so a future caller that asks in the wrong order still gets the
     * song rather than the playlist it happened to be filed under.
     */
    @Test
    fun `a song inside a playlist is a song, not a playlist`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefgh123"
        assertNotNull(videoId(url))
        assertNull(parse(url))
    }

    @Test
    fun `a plain video link names no entity`() {
        assertNull(parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(parse("https://youtu.be/dQw4w9WgXcQ"))
    }

    /**
     * A handle is not a channel id, and turning one into the other means asking YouTube. This path
     * decides cheaply whether a link is ours at all, so it declines rather than reaching out.
     */
    @Test
    fun `an @handle is not accepted`() {
        assertNull(parse("https://www.youtube.com/@someartist"))
    }

    @Test
    fun `an unknown browse id names nothing`() {
        assertNull(parse("https://music.youtube.com/browse/FEmusic_home"))
    }

    @Test
    fun `other hosts name nothing`() {
        assertNull(parse("https://example.com/playlist?list=PLabcdefgh123"))
        assertNull(parse("https://youtu.be/playlist?list=PLabcdefgh123"))
    }

    @Test
    fun `a playlist link with no id names nothing`() {
        assertNull(parse("https://www.youtube.com/playlist"))
        assertNull(parse("https://www.youtube.com/playlist?list="))
    }

    /** `m.` and `www.` are the same host, and a share from a phone carries the first. */
    @Test
    fun `mobile and bare hosts are the same host`() {
        assertEquals(YouTubeEntityKind.PLAYLIST, parse("https://m.youtube.com/playlist?list=PLx1")?.kind)
        assertEquals(YouTubeEntityKind.PLAYLIST, parse("https://youtube.com/playlist?list=PLx1")?.kind)
    }
}
