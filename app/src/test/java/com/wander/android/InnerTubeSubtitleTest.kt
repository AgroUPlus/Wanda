package com.wander.android

import com.wander.android.data.sources.ytmusic.InnerTubeSubtitle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `Song • Artist • Album • 3:45` line, taken apart.
 *
 * Every bug this class has produced looked the same from the outside: a track credited to somebody
 * who is not a person. "Song", then "2023", then "Single" — each one a token that happened to sit
 * where an artist usually sits. The rule under test is that position alone never decides.
 */
class InnerTubeSubtitleTest {

    /** Builds a runs array, with `•` separators, marking which tokens link to an artist page. */
    private fun runs(vararg parts: Pair<String, Boolean>): JsonArray = buildJsonArray {
        parts.forEachIndexed { index, (text, linked) ->
            if (index > 0) add(buildJsonObject { put("text", " • ") })
            add(
                buildJsonObject {
                    put("text", text)
                    if (linked) {
                        put(
                            "navigationEndpoint",
                            buildJsonObject {
                                put(
                                    "browseEndpoint",
                                    buildJsonObject {
                                        put("browseId", "UC_$text")
                                        put(
                                            "browseEndpointContextSupportedConfigs",
                                            buildJsonObject {
                                                put(
                                                    "browseEndpointContextMusicConfig",
                                                    buildJsonObject {
                                                        put("pageType", "MUSIC_PAGE_TYPE_ARTIST")
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    /** The reported bug: an album page crediting the record to an artist called "Single". */
    @Test
    fun `a release line names nobody`() {
        val subtitle = InnerTubeSubtitle.of(runs("Single" to false, "2023" to false))

        assertNull("a kind and a year is not a credit", subtitle.artist)
        assertEquals(2023, subtitle.year)
    }

    @Test
    fun `an album release line names nobody either`() {
        assertNull(InnerTubeSubtitle.of(runs("Album" to false, "2019" to false)).artist)
    }

    /** A linked artist always wins, label or no label. */
    @Test
    fun `a linked artist is the credit`() {
        val subtitle = InnerTubeSubtitle.of(
            runs("Song" to false, "yuri" to true, "Heart 111" to false, "3:12" to false)
        )

        assertEquals("yuri", subtitle.artist)
        assertEquals("yuri", subtitle.linkedArtist)
    }

    /**
     * An upload YouTube never matched to an artist: plain text, no links, and the name really is
     * the first token. This is the case the positional fallback exists for and must keep working.
     */
    @Test
    fun `an unlinked uploader is still a credit`() {
        val subtitle = InnerTubeSubtitle.of(runs("Some Uploader" to false, "4:20" to false))

        assertEquals("Some Uploader", subtitle.artist)
        assertNull("no link, so no confident credit", subtitle.linkedArtist)
    }

    /** Counts are quantities, never names — but they must not swallow the name beside them. */
    @Test
    fun `a play count is not an artist`() {
        assertEquals(
            "Lofi Girl",
            InnerTubeSubtitle.of(runs("Lofi Girl" to false, "1.2M plays" to false)).artist
        )
    }

    /**
     * Artists whose names begin with a number.
     *
     * The count rule has to be narrow enough to let these through: rejecting a real artist as a
     * statistic is a worse failure than letting an unsuffixed "3 views" be read as a name, and
     * YouTube prints the suffixed form for anything with an audience.
     */
    @Test
    fun `an artist named like a number is still an artist`() {
        for (name in listOf("50 Cent", "21 Savage", "3 Doors Down")) {
            assertEquals(name, InnerTubeSubtitle.of(runs(name to false, "3:41" to false)).artist)
        }
    }

    /**
     * The album field had the same positional flaw as the artist one, a column over: it rejected
     * only durations, so `Artist • 15M views` filed the song under an album called "15M views".
     * Two hundred and forty-two rows in one real library.
     */
    @Test
    fun `a view count is not an album`() {
        assertNull(InnerTubeSubtitle.of(runs("Ado" to true, "15M views" to false)).album)
    }

    /** A separator that survived tokenising is not an album either. */
    @Test
    fun `a stray separator is not an album`() {
        assertNull(InnerTubeSubtitle.of(runs("The Vanished People" to true, "&" to false)).album)
    }

    /**
     * But a year *is* a perfectly good record title, even though it can never be an artist — so
     * the album test has to be looser than the artist one in exactly this one way.
     */
    @Test
    fun `a year can be an album title`() {
        assertEquals(
            "1989",
            InnerTubeSubtitle.of(runs("Taylor Swift" to true, "1989" to false, "3:31" to false)).album
        )
    }
}
