package com.wander.android.data.replay

import java.time.LocalDate
import java.time.ZoneId

/**
 * When Agro Replay offers itself, and for which year.
 *
 * Pure and dateless by itself, so the season can be tested without waiting for December.
 *
 * The window runs from 1 December to 15 January and always names the year that is ending or has
 * just ended. December is early enough to feel like the end of the year and late enough that the
 * recap is nearly complete; the fortnight into January is for everyone who was away. After that it
 * stops appearing on its own — a recap that ambushes somebody in March is not a celebration — but
 * it stays reachable from Settings all year.
 */
internal object ReplayAvailability {

    /** The year to recap today, or null if this is not the season. */
    fun offeredYear(today: LocalDate): Int? = when {
        today.monthValue == DECEMBER -> today.year
        today.monthValue == JANUARY && today.dayOfMonth <= LAST_JANUARY_DAY -> today.year - 1
        else -> null
    }

    /**
     * Whether to put the offer in front of somebody right now.
     *
     * [lastSeenYear] is the watermark: the most recent year whose recap this device has already
     * offered. A year rather than a flag, so next December offers itself without anything having to
     * reset it.
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

    private const val JANUARY = 1
    private const val DECEMBER = 12
    private const val LAST_JANUARY_DAY = 15
}
