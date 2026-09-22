package com.wander.android

import com.wander.android.core.database.dao.PlayedTrack
import com.wander.android.data.replay.ReplayComputer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The local half of Agro Replay, which has to agree with the server's.
 *
 * These mirror the cases in Agro's `src/stats_wrapped.rs` deliberately: the same recap is computed
 * in two places, and a device that showed a different streak after pairing would read as data loss.
 * When one side's rules change, both sets of tests change together.
 */
class ReplayComputerTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun play(
        artist: String,
        title: String,
        at: String,
        zone: ZoneId = utc,
        durationMs: Long = 300_000L,
        genre: String? = "Electronic"
    ) = PlayedTrack(
        playedAt = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli(),
        trackId = "$artist:$title",
        title = title,
        artist = artist,
        album = "An Album",
        artworkUrl = null,
        durationMs = durationMs,
        genre = genre
    )

    private fun compute(
        plays: List<PlayedTrack>,
        priorArtists: Set<String> = emptySet(),
        zone: ZoneId = utc,
        year: Int = 2026
    ) = ReplayComputer.compute(year, plays, priorArtists, zone, generatedAtMillis = 0L)

    // ── The streak ──────────────────────────────────────────────────────────────────────────

    /** The defect this replaced on both sides: active days counted as if they were a run. */
    @Test
    fun `a streak is the longest run, not the count of active days`() {
        val days = setOf(1L, 2L, 3L, 10L, 20L)
        assertEquals(5, days.size)
        assertEquals(3, ReplayComputer.longestStreak(days))
    }

    @Test
    fun `a streak crosses the turn of the year`() {
        // Epoch days are absolute, so 31 December and 1 January are simply adjacent.
        val newYear = LocalDateTime.parse("2027-01-01T00:00").toLocalDate().toEpochDay()
        val days = setOf(newYear - 2, newYear - 1, newYear, newYear + 1)
        assertEquals(4, ReplayComputer.longestStreak(days))
    }

    @Test
    fun `no plays is no streak`() {
        assertEquals(0, ReplayComputer.longestStreak(emptySet()))
    }

    @Test
    fun `one day is a streak of one`() {
        assertEquals(1, ReplayComputer.longestStreak(setOf(500L)))
    }

    // ── The recap ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `counts the year and names what was new in it`() {
        val report = compute(
            plays = listOf(
                play("Daft Punk", "One More Time", "2026-02-10T10:00"),
                play("Aphex Twin", "Windowlicker", "2026-08-20T18:00"),
                play("Aphex Twin", "Xtal", "2026-08-21T18:00")
            ),
            priorArtists = setOf("Daft Punk")
        )

        assertEquals(3, report.totalPlays)
        assertEquals(15L, report.totalMinutes)
        assertEquals(2, report.totalArtists)
        assertEquals(1, report.newArtistsCount)
        assertEquals(3, report.activeDaysCount)
        assertEquals(2, report.longestStreakDays)
        assertEquals(1L, report.byMonth[1])
        assertEquals(2L, report.byMonth[7])
        assertEquals(12, report.byMonth.size)
        assertEquals(24, report.byHour.size)
    }

    /** A top-N is not a total. Both sides report the distinct count separately for this reason. */
    @Test
    fun `the distinct artist count is not the length of the top ten`() {
        val plays = (1..15).map { play("Artist $it", "A Song", "2026-03-0${it % 9 + 1}T12:00") }
        val report = compute(plays)

        assertEquals(10, report.topArtists.size)
        assertEquals(15, report.totalArtists)
    }

    @Test
    fun `ranks by count, then by name, so equal counts do not jitter`() {
        val report = compute(
            listOf(
                play("Zebra", "A", "2026-01-01T10:00"),
                play("Alpaca", "B", "2026-01-01T11:00"),
                play("Moth", "C", "2026-01-02T10:00"),
                play("Moth", "D", "2026-01-02T11:00")
            )
        )

        assertEquals(listOf("Moth", "Alpaca", "Zebra"), report.topArtists.map { it.name })
        assertEquals(2L, report.topArtists.first().value)
    }

    @Test
    fun `tracks and albums are named the way the server names them`() {
        val report = compute(listOf(play("Aphex Twin", "Xtal", "2026-05-05T09:00")))

        assertEquals("Xtal — Aphex Twin", report.topTracks.single().name)
        assertEquals("An Album — Aphex Twin", report.topAlbums.single().name)
    }

    @Test
    fun `an untagged genre is left out rather than counted as blank`() {
        val report = compute(
            listOf(
                play("A", "One", "2026-05-05T09:00", genre = null),
                play("B", "Two", "2026-05-05T10:00", genre = "  "),
                play("C", "Three", "2026-05-05T11:00", genre = "Ambient")
            )
        )

        assertEquals(listOf("Ambient"), report.topGenres.map { it.name })
    }

    // ── Local time ──────────────────────────────────────────────────────────────────────────

    /** The peak hour is the listener's hour, which is the whole reason a zone is passed in. */
    @Test
    fun `the peak hour is read in the listener's zone`() {
        val plays = listOf(
            play("Aphex Twin", "Xtal", "2026-06-15T02:00"),
            play("Aphex Twin", "Ageispolis", "2026-06-15T02:30")
        )

        assertEquals(2, compute(plays).topHourLocal)
        // Berlin is UTC+2 in June.
        assertEquals(4, compute(plays, zone = berlin).topHourLocal)
    }

    @Test
    fun `an empty year is an empty recap, not a zeroed one`() {
        val report = compute(emptyList())

        assertTrue(report.isEmpty)
        assertEquals(0L, report.totalPlays)
        assertEquals(0, report.totalArtists)
        assertEquals(0, report.longestStreakDays)
        assertNull("no peak hour without plays", report.topHourLocal)
        assertEquals(12, report.byMonth.size)
        assertTrue(report.topArtists.isEmpty())
    }

    /** A local recap knows about one device, so it reports no breakdown at all rather than "1". */
    @Test
    fun `a local recap claims nothing about devices`() {
        val report = compute(listOf(play("A", "One", "2026-05-05T09:00")))

        assertTrue(report.byDevice.isEmpty())
        assertTrue("and says it covers only this device", !report.isFleetWide)
    }
}
