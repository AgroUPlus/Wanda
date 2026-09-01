package com.wander.android

import com.wander.android.data.repository.CanonicalMetadataMerge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue is allowed to tidy a title. It is not allowed to rename anyone's music.
 *
 * Every entry arrives from another device running the same importers over the same imperfect tags,
 * so "the catalogue says otherwise" is not evidence of anything on its own. The one thing it does
 * know better is when two spellings are the same name with decoration on one of them — which is
 * exactly the case a YouTube upload creates and a Navidrome tag does not.
 */
class CanonicalMetadataMergeTest {

    /** The case the whole thing exists for. */
    @Test
    fun `release noise is stripped when the underlying title agrees`() {
        assertTrue(
            CanonicalMetadataMerge.improvesOnTitle("All I Need (Official Video) [HQ]", "All I Need")
        )
    }

    /** A blank row takes anything, since there is nothing to lose. */
    @Test
    fun `an empty title takes the catalogue's`() {
        assertTrue(CanonicalMetadataMerge.improvesOnTitle("", "All I Need"))
        assertFalse(CanonicalMetadataMerge.improvesOnTitle("All I Need", ""))
    }

    /** Two genuinely different names is a disagreement, and the row wins a disagreement. */
    @Test
    fun `a different title is never taken`() {
        assertFalse(CanonicalMetadataMerge.improvesOnTitle("All I Need", "Weird Fishes"))
    }

    /**
     * The direction matters.
     *
     * The same rule read backwards would let a device holding the messier tag push its decoration
     * onto everyone else, which is the failure mode this must never have: the catalogue would
     * converge on the worst title anyone owns rather than the best.
     */
    @Test
    fun `noise is never added to a clean title`() {
        assertFalse(
            CanonicalMetadataMerge.improvesOnTitle("All I Need", "All I Need (Official Video) [HQ]")
        )
    }

    /** A variant marker is not noise: it names a different performance and must survive. */
    @Test
    fun `a live marker is not tidied away`() {
        assertFalse(CanonicalMetadataMerge.improvesOnTitle("All I Need (Live)", "All I Need"))
    }

    /** Identical strings are not an improvement, and writing them would be a pointless write. */
    @Test
    fun `an identical title is not an improvement`() {
        assertFalse(CanonicalMetadataMerge.improvesOnTitle("All I Need", "All I Need"))
    }

    /** Artist and album get the blank rule only — a differing credit is information, not noise. */
    @Test
    fun `artist and album are only ever filled in`() {
        assertTrue(CanonicalMetadataMerge.fills("", "Radiohead"))
        assertFalse(CanonicalMetadataMerge.fills("XLRecordings", "Radiohead"))
        assertFalse(CanonicalMetadataMerge.fills("", "  "))
    }
}
