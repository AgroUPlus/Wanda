package com.wander.android.data.podcast

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter

data class OpmlFeed(val title: String?, val feedUrl: String)

/** Reads and writes the OPML files podcast apps use to move subscriptions between them. */
object Opml {

    /** Every `<outline xmlUrl>`, nested in folders or not. Outlines without a feed URL are folders or noise. */
    fun parse(parser: XmlPullParser): List<OpmlFeed> {
        val feeds = mutableListOf<OpmlFeed>()
        var sawOpml = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.lowercase()) {
                "opml" -> sawOpml = true
                "outline" -> {
                    val url = parser.getAttributeValue(null, "xmlUrl")?.trim()?.ifBlank { null }
                    if (url != null) {
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                        feeds += OpmlFeed(title?.trim()?.ifBlank { null }, url)
                    }
                }
            }
        }
        if (!sawOpml) throw FeedParseException("Not an OPML file")
        return feeds
    }

    fun write(feeds: List<OpmlFeed>, title: String): String {
        val out = StringWriter()
        val xml: XmlSerializer = Xml.newSerializer()
        xml.setOutput(out)
        xml.startDocument("UTF-8", true)
        xml.startTag(null, "opml").attribute(null, "version", "2.0")
        xml.startTag(null, "head").startTag(null, "title").text(title).endTag(null, "title").endTag(null, "head")
        xml.startTag(null, "body")
        feeds.forEach { feed ->
            xml.startTag(null, "outline")
            xml.attribute(null, "type", "rss")
            xml.attribute(null, "text", feed.title ?: feed.feedUrl)
            xml.attribute(null, "xmlUrl", feed.feedUrl)
            xml.endTag(null, "outline")
        }
        xml.endTag(null, "body").endTag(null, "opml")
        xml.endDocument()
        return out.toString()
    }
}
