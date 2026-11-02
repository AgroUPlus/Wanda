package com.wander.android.data.sources.musicbrainz

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicBrainzQueryTest {

    @Test
    fun `quotes in a name cannot close the phrase`() {
        assertEquals("""artist:"\"Weird Al\" Yankovic"""", MusicBrainzQuery.artist("\"Weird Al\" Yankovic"))
    }

    @Test
    fun `every lucene operator is escaped`() {
        assertEquals("""AC\/DC""", MusicBrainzQuery.escape("AC/DC"))
        assertEquals("""\+44""", MusicBrainzQuery.escape("+44"))
        assertEquals("""Sunn O\)\)\)""", MusicBrainzQuery.escape("Sunn O)))"))
        assertEquals("""a\&\&b\|\|c\!""", MusicBrainzQuery.escape("a&&b||c!"))
        assertEquals("""\(\)\{\}\[\]\^\~\*\?\:\\\-""", MusicBrainzQuery.escape("""(){}[]^~*?:\-"""))
    }

    @Test
    fun `plain names are left alone`() {
        assertEquals("Björk", MusicBrainzQuery.escape("Björk"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttled calls start at least an interval apart`() = runTest {
        val throttle = RequestThrottle(intervalMs = 1_000L, nowMs = { currentTime })
        val starts = mutableListOf<Long>()
        repeat(3) { launch { throttle.await(); starts += currentTime } }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }
}
