package com.wander.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun comparesEachNumberNumerically() {
        assertTrue(isNewer("1.10.0", "1.9.0")!!)
        assertTrue(isNewer("2.0.0", "1.99.99")!!)
        assertFalse(isNewer("1.6.0", "1.6.0")!!)
        assertFalse(isNewer("1.5.12", "1.6.0")!!)
    }

    @Test
    fun aMissingTrailingPartCountsAsZero() {
        assertFalse(isNewer("1.7", "1.7.0")!!)
        assertTrue(isNewer("1.7.1", "1.7")!!)
    }

    @Test
    fun acceptsTheTagPrefixAndIgnoresBuildMetadata() {
        assertTrue(isNewer("v1.7.0", "1.6.0")!!)
        assertFalse(isNewer("1.7.0+build.5", "1.7.0")!!)
    }

    @Test
    fun aPreReleaseRanksBelowItsRelease() {
        assertFalse(isNewer("1.7.0-beta1", "1.7.0")!!)
        assertTrue(isNewer("1.7.0", "1.7.0-beta1")!!)
        assertTrue(isNewer("1.7.0-beta1", "1.6.0")!!)
        assertFalse(isNewer("1.6.0-beta.2", "1.6.0")!!)
    }

    @Test
    fun preReleasesFollowSemverPrecedence() {
        // The example chain from semver.org §11.4, each newer than the one before.
        val chain = listOf(
            "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
            "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0"
        )
        chain.zipWithNext().forEach { (older, newer) ->
            assertTrue("$newer > $older", isNewer(newer, older)!!)
            assertFalse("$older < $newer", isNewer(older, newer)!!)
        }
    }

    @Test
    fun aTagThatIsNotAVersionIsNotCompared() {
        assertNull(isNewer("embedder-v1", "1.6.0"))
        assertNull(isNewer("1..0", "1.6.0"))
        assertNull(isNewer("1.7.0-", "1.6.0"))
        assertNull(isNewer("1.7.0-beta_1", "1.6.0"))
        assertNull(isNewer("1.7.0", ""))
    }

    @Test
    fun parsesNumbersAndPreReleaseIdentifiers() {
        assertEquals(
            ReleaseVersion(listOf(1L, 7L, 0L), listOf("beta", "1")),
            ReleaseVersion.parse("v1.7.0-beta.1+sha.abc")
        )
    }
}
