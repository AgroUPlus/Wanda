package com.wander.android

import com.wander.android.data.sources.ytmusic.youTubeVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which YouTube URLs name a single video.
 *
 * The list of path segments here is load-bearing and gains entries silently: `live` was missing,
 * and because the manifest's `youtube.com` filter carries no path restriction, Wanda offered
 * itself in the share chooser for every broadcast and then refused the link it had just claimed.
 * A link form that is not on this list is not "handled badly", it is advertised and then rejected.
 */
class YouTubeLinkParsingTest {

    private val id = "dQw4w9WgXcQ" // 11 chars, the fixed YouTube id length

    /** Parses a URL without `Uri`, which has no behaviour off-device. */
    private fun parse(url: String): String? {
        val afterScheme = url.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?')
        val path = afterScheme.substringAfter('/', "").substringBefore('?')
        val query = afterScheme.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        return youTubeVideoId(
            host = host,
            pathSegments = path.split('/').filter { it.isNotEmpty() },
            videoQueryParam = { query[it] }
        )
    }

    @Test
    fun `a broadcast link from YouTube's share sheet resolves`() {
        // The form that was refused. `si` is the tracking parameter YouTube appends to shares.
        assertEquals(id, parse("https://www.youtube.com/live/$id?si=Xk29fLp0"))
        assertEquals(id, parse("https://youtube.com/live/$id"))
        assertEquals(id, parse("https://m.youtube.com/live/$id"))
    }

    @Test
    fun `the forms that already worked still do`() {
        assertEquals(id, parse("https://music.youtube.com/watch?v=$id"))
        assertEquals(id, parse("https://www.youtube.com/watch?v=$id&list=RDsomething"))
        assertEquals(id, parse("https://youtu.be/$id"))
        assertEquals(id, parse("https://www.youtube.com/shorts/$id"))
        assertEquals(id, parse("https://www.youtube.com/embed/$id"))
    }

    @Test
    fun `a URL with no single video behind it is refused`() {
        assertNull(parse("https://www.youtube.com/channel/UCsomethingorother"))
        assertNull(parse("https://www.youtube.com/playlist?list=PLabc"))
        assertNull(parse("https://www.youtube.com/results?search_query=toby+fox"))
        assertNull(parse("https://example.com/live/$id"))
    }

    /**
     * `/live` with nothing after it is a channel's current broadcast, not an id.
     *
     * The length check is what catches it. Without one, the segment after `live` — absent here —
     * would have been passed along as an id and could only 404.
     */
    @Test
    fun `a path segment that is not an id is refused`() {
        assertNull(parse("https://www.youtube.com/live"))
        assertNull(parse("https://www.youtube.com/live/"))
        assertNull(parse("https://www.youtube.com/live/tooshort"))
        assertNull(parse("https://www.youtube.com/live/waytoolongtobeavideoid"))
        assertNull(parse("https://www.youtube.com/live/has.a.dot!"))
    }
}
