package com.wander.android

import com.wander.android.data.repository.TrackDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every alternative the noise and variant patterns are supposed to recognise.
 *
 * [TrackDeduplicatorTest] covers the behaviour that matters to a user — what merges and what stays
 * apart. This covers the vocabulary underneath it, one term at a time, so the patterns can be
 * rewritten without the rewrite quietly dropping a word. Losing a noise term costs a merge that
 * should have happened; losing a variant term merges a live take into the studio cut, which is the
 * failure the deduplicator is most required not to make.
 */
class TrackDeduplicatorPatternTest {

    private val noiseTerms = listOf(
        "remaster", "remastered", "remastered 2011", "remaster 2011", "2011 remaster",
        "official video", "official audio", "official music video",
        "lyric", "lyrics", "lyrics video", "lyric video",
        "album version", "single version", "original mix",
        "explicit", "clean", "hd", "hq", "visualizer", "mv"
    )

    private val variantTerms = listOf(
        "live", "acoustic", "unplugged", "remix", "rmx", "demo", "instrumental", "karaoke",
        "reprise", "edit", "mix", "version", "cover", "session", "extended", "club", "dub",
        "slowed", "sped up", "spedup", "orchestral", "piano", "deluxe", "bonus"
    )

    @Test
    fun `every noise term is stripped from a title`() {
        for (term in noiseTerms) {
            assertEquals(
                "noise term \"$term\" should not survive normalisation",
                "midnight city",
                TrackDeduplicator.normalizeTitle("Midnight City ($term)")
            )
        }
    }

    @Test
    fun `every variant term is recognised as a variant`() {
        for (term in variantTerms) {
            assertTrue(
                "variant term \"$term\" should register as a variant marker",
                TrackDeduplicator.variantsOf("Midnight City ($term)").isNotEmpty()
            )
        }
    }

    @Test
    fun `every variant term is stripped from the normalised title`() {
        for (term in variantTerms) {
            assertEquals(
                "variant term \"$term\" should not survive normalisation",
                "midnight city",
                TrackDeduplicator.normalizeTitle("Midnight City ($term)")
            )
        }
    }

    @Test
    fun `noise and variant terms are only matched as whole words`() {
        // "livestream" is not "live", and "editorial" is not "edit". Substring matches here would
        // split a catalogue apart on words that merely contain a marker.
        assertTrue(TrackDeduplicator.variantsOf("Livestream").isEmpty())
        assertTrue(TrackDeduplicator.variantsOf("Editorial").isEmpty())
        assertTrue(TrackDeduplicator.variantsOf("Cleanser").isEmpty())
        assertEquals("livestream", TrackDeduplicator.normalizeTitle("Livestream"))
    }

    @Test
    fun `featured artist clauses are removed from titles and artists`() {
        for (marker in listOf("feat", "feat.", "ft", "ft.", "featuring", "with")) {
            assertEquals(
                "marker \"$marker\" should end the title",
                "midnight city",
                TrackDeduplicator.normalizeTitle("Midnight City $marker Someone Else")
            )
        }
    }

    @Test
    fun `a title of nothing but markers normalises to empty rather than throwing`() {
        assertEquals("", TrackDeduplicator.normalizeTitle("(Live) (Remastered 2011)"))
    }
}
