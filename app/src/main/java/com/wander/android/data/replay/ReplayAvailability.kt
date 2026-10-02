package com.wander.android.data.replay

import java.time.LocalDate
import java.time.ZoneId

/**
 * When Agro Replay offers itself, and for which year.
 *
 * Pure and dateless by itself, so the season can be tested without waiting for November.
 *
 * The window is the last two months of the year, November and December, and always names the year
 * that is ending: late enough to feel like the end of the year, with most of it already listened
 * to. After that it stops appearing on its own — a recap that ambushes somebody in March is not a
 * celebration — but it stays reachable from Settings › About all year.
 */
internal object ReplayAvailability {

    /** The year to recap today, or null if this is not the season. */
    fun offeredYear(today: LocalDate): Int? =
        today.year.takeIf { today.monthValue >= FIRST_MONTH }

    /**
     * Whether to put the offer in front of somebody right now.
     *
     * [lastSeenYear] is the watermark: the most recent year somebody opened, or asked not to be
     * shown again. A year rather than a flag, so next November offers itself without anything having
     * to reset it.
     */
    fun shouldOffer(today: LocalDate, lastSeenYear: Int): Int? =
        offeredYear(today)?.takeIf { it > lastSeenYear }

    /**
     * The year to show when somebody asks for a recap out of season, from Settings.
     *
     * The year that is over, because the one in progress is not a recap yet — except during the
     * season itself, when it is whatever the season is offering.
     */
    fun yearOnDemand(today: LocalDate): Int = offeredYear(today) ?: (today.year - 1)

    fun offeredYear(zone: ZoneId = ZoneId.systemDefault()): Int? = offeredYear(LocalDate.now(zone))

    /** November: the first of the year's last two months. */
    private const val FIRST_MONTH = 11
}
