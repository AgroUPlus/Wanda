package com.wander.android

import com.wander.android.data.replay.ReplayAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** When the recap appears on its own, and which year it is about when it does. */
class ReplayAvailabilityTest {

    private fun on(date: String) = LocalDate.parse(date)

    @Test
    fun `the season opens on the first of december`() {
        assertNull(ReplayAvailability.offeredYear(on("2026-11-30")))
        assertEquals(2026, ReplayAvailability.offeredYear(on("2026-12-01")))
        assertEquals(2026, ReplayAvailability.offeredYear(on("2026-12-31")))
    }

    /** January still recaps the year that ended, not the four days of the one that started. */
    @Test
    fun `january recaps the year that just ended`() {
        assertEquals(2026, ReplayAvailability.offeredYear(on("2027-01-01")))
        assertEquals(2026, ReplayAvailability.offeredYear(on("2027-01-15")))
        assertNull(ReplayAvailability.offeredYear(on("2027-01-16")))
    }

    @Test
    fun `the rest of the year offers nothing`() {
        for (date in listOf("2026-02-01", "2026-06-15", "2026-09-22", "2026-11-15")) {
            assertNull("no offer on $date", ReplayAvailability.offeredYear(on(date)))
        }
    }

    /** A watermark, not a flag: seeing last year's recap must not suppress this year's. */
    @Test
    fun `a year already seen is not offered again`() {
        assertNull(ReplayAvailability.shouldOffer(on("2026-12-05"), lastSeenYear = 2026))
        assertEquals(2026, ReplayAvailability.shouldOffer(on("2026-12-05"), lastSeenYear = 2025))
        assertEquals(2026, ReplayAvailability.shouldOffer(on("2026-12-05"), lastSeenYear = 0))
    }

    @Test
    fun `next december offers itself without anything resetting`() {
        assertNull(ReplayAvailability.shouldOffer(on("2027-01-10"), lastSeenYear = 2026))
        assertEquals(2027, ReplayAvailability.shouldOffer(on("2027-12-02"), lastSeenYear = 2026))
    }

    /** Asked for in March, the answer is the year that is actually over. */
    @Test
    fun `out of season it offers the last complete year`() {
        assertEquals(2025, ReplayAvailability.yearOnDemand(on("2026-03-10")))
        // In season, it agrees with the season rather than skipping a year.
        assertEquals(2026, ReplayAvailability.yearOnDemand(on("2026-12-10")))
        assertEquals(2026, ReplayAvailability.yearOnDemand(on("2027-01-05")))
    }
}
