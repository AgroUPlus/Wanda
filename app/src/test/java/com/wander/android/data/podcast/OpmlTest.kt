package com.wander.android.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser

class OpmlTest {

    private fun parse(xml: String) = Opml.parse(KXmlParser().also { it.setInput(xml.byteInputStream(), null) })

    @Test
    fun `finds feeds nested in folders and skips the folders`() {
        val feeds = parse(
            """
            <opml version="2.0"><body>
              <outline text="Folder">
                <outline type="rss" text="One" xmlUrl="https://a.com/feed"/>
              </outline>
              <outline type="rss" title="Two" text="ignored" xmlUrl="https://b.com/feed"/>
            </body></opml>
            """.trimIndent()
        )

        assertEquals(
            listOf(OpmlFeed("One", "https://a.com/feed"), OpmlFeed("Two", "https://b.com/feed")),
            feeds
        )
    }

    @Test
    fun `a file that is not opml is refused`() {
        assertThrows(FeedParseException::class.java) { parse("<rss><channel/></rss>") }
    }
}
