package com.wander.android

import com.wander.android.data.replay.ReplayCircle
import com.wander.android.data.replay.ReplayEntry
import com.wander.android.data.replay.ReplayReport
import com.wander.android.ui.screens.replay.ReplayCard
import com.wander.android.ui.screens.replay.buildReplayDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the story shows, and — mostly — what it refuses to show.
 *
 * The rule every case here is an instance of: a card exists only when there is something behind it.
 * A shorter recap is the correct answer for a sparse year; a padded one would be inventing numbers.
 */
class ReplayDeckTest {

    private fun report(
        plays: Long = 100,
        topArtists: List<ReplayEntry> = listOf(ReplayEntry("Aphex Twin", 40)),
        topTracks: List<ReplayEntry> = listOf(ReplayEntry("Xtal — Aphex Twin", 12)),
        topGenres: List<ReplayEntry> = listOf(ReplayEntry("Ambient", 30)),
        byMonth: List<Long> = List(12) { 5L },
        byHour: List<Long> = List(24) { 2L },
        topHourLocal: Int? = 21,
        longestStreakDays: Int = 9,
        newArtistsCount: Int = 7,
        byDevice: List<ReplayEntry> = emptyList(),
        chartPercentile: Int? = null,
        circle: ReplayCircle? = null,
        isFleetWide: Boolean = false
    ) = ReplayReport(
        year = 2026,
        isFleetWide = isFleetWide,
        totalMinutes = 4_000,
        totalPlays = plays,
        topArtists = topArtists,
        topTracks = topTracks,
        topAlbums = emptyList(),
        topGenres = topGenres,
        topHourLocal = topHourLocal,
        longestStreakDays = longestStreakDays,
        activeDaysCount = 200,
        newArtistsCount = newArtistsCount,
        totalArtists = 120,
        byMonth = byMonth,
        byHour = byHour,
        byDevice = byDevice,
        chartPercentile = chartPercentile,
        circle = circle
    )

    private fun circle(members: List<String>) = ReplayCircle(
        members = members,
        anthemTitle = "Windowlicker",
        anthemArtist = "Aphex Twin",
        anthemPlays = 30,
        trendsetter = "beta",
        trendsetterFirsts = 4,
        closestFriend = "beta",
        closestScore = 71
    )

    private inline fun <reified T> List<ReplayCard>.has(): Boolean = any { it is T }

    @Test
    fun `a full year tells the whole story`() {
        val deck = buildReplayDeck(
            report(
                byDevice = listOf(ReplayEntry("phone", 80), ReplayEntry("laptop", 20)),
                chartPercentile = 88,
                circle = circle(listOf("me", "beta")),
                isFleetWide = true
            )
        )

        assertTrue(deck.has<ReplayCard.Intro>())
        assertTrue(deck.has<ReplayCard.Minutes>())
        assertTrue(deck.has<ReplayCard.Devices>())
        assertTrue(deck.has<ReplayCard.Charts>())
        assertTrue(deck.has<ReplayCard.Circle>())
        assertTrue("the outro is always last", deck.last() is ReplayCard.Outro)
    }

    /** An unpaired phone has no server and no friends to compare against. */
    @Test
    fun `a local recap drops every card that needs a server`() {
        val deck = buildReplayDeck(report())

        assertFalse(deck.has<ReplayCard.Devices>())
        assertFalse(deck.has<ReplayCard.Charts>())
        assertFalse(deck.has<ReplayCard.Circle>())
        assertTrue("but still tells the personal half", deck.has<ReplayCard.TopArtists>())
    }

    /** One device is not a breakdown. */
    @Test
    fun `a single device is not a devices card`() {
        val deck = buildReplayDeck(report(byDevice = listOf(ReplayEntry("phone", 100))))
        assertFalse(deck.has<ReplayCard.Devices>())
    }

    /** A circle of one is the server's normal answer for somebody with no friends on it. */
    @Test
    fun `a circle of one is not a circle card`() {
        val deck = buildReplayDeck(report(circle = circle(listOf("me"))))
        assertFalse(deck.has<ReplayCard.Circle>())

        val shared = buildReplayDeck(report(circle = circle(listOf("me", "beta"))))
        assertTrue(shared.has<ReplayCard.Circle>())
    }

    /** A suppressed standing arrives as a null percentile, and must not become "top 0%". */
    @Test
    fun `a suppressed chart standing is not a charts card`() {
        assertFalse(buildReplayDeck(report(chartPercentile = null)).has<ReplayCard.Charts>())
        // Zero is a real answer for the quietest listener, and is still a card.
        assertTrue(buildReplayDeck(report(chartPercentile = 0)).has<ReplayCard.Charts>())
    }

    /** A library with no genre tags gets no genres card rather than an empty one. */
    @Test
    fun `absent sources drop their own card and nothing else`() {
        val deck = buildReplayDeck(
            report(
                topGenres = emptyList(),
                topHourLocal = null,
                newArtistsCount = 0,
                longestStreakDays = 1
            )
        )

        assertFalse(deck.has<ReplayCard.Genres>())
        assertFalse(deck.has<ReplayCard.Hours>())
        assertFalse(deck.has<ReplayCard.Discovery>())
        assertFalse("a one-day streak is not a streak", deck.has<ReplayCard.Streak>())
        assertTrue(deck.has<ReplayCard.Minutes>())
        assertTrue(deck.has<ReplayCard.TopArtists>())
    }

    /** A year with nothing in it says so once, rather than eleven times with zeros. */
    @Test
    fun `an empty year is two cards`() {
        val deck = buildReplayDeck(report(plays = 0))

        assertEquals(2, deck.size)
        assertTrue(deck.first() is ReplayCard.Silence)
        assertTrue(deck.last() is ReplayCard.Outro)
    }

    @Test
    fun `the top song is split back into a title and an artist`() {
        val deck = buildReplayDeck(report(topTracks = listOf(ReplayEntry("Sunset — Reprise — Boards of Canada", 9))))
        val song = deck.filterIsInstance<ReplayCard.TopSong>().single()

        assertEquals("Sunset — Reprise", song.title)
        assertEquals("Boards of Canada", song.artist)
    }

    @Test
    fun `the shape card names the heaviest month`() {
        val months = MutableList(12) { 1L }.also { it[7] = 99L }
        val shape = buildReplayDeck(report(byMonth = months))
            .filterIsInstance<ReplayCard.Shape>()
            .single()

        assertEquals("August, zero-indexed", 7, shape.peakMonth)
    }
}
