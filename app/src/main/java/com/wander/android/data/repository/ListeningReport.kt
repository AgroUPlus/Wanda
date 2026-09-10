package com.wander.android.data.repository

import androidx.compose.runtime.Immutable
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.StatsPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * A stretch of time the statistics screen is looking at.
 *
 * [offset] counts windows backwards from the present: 0 is the one ending now, 1 the one before
 * it, and so on. It exists so the screen can be *paged* rather than only ever showing the last
 * seven days — a listening history you cannot scroll back through is a dashboard, not a history.
 *
 * The window is a whole number of days ending at the end of today, rather than the last N×24
 * hours, so its label names dates a person recognises and so today's listening is counted in the
 * window it happened in.
 */
@Immutable
data class StatsWindow(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val offset: Int = 0
) {
    /** All time has no edges to step over. */
    val isPageable: Boolean get() = period != StatsPeriod.ALL

    /** Further into the past. */
    fun earlier(): StatsWindow = if (isPageable) copy(offset = offset + 1) else this

    /** Back towards the present. Never past it — there is nothing to show in the future. */
    fun later(): StatsWindow = if (offset > 0) copy(offset = offset - 1) else this

    val isLatest: Boolean get() = offset == 0

    /**
     * Half-open bounds, `start` inclusive and `end` exclusive, so this window and the one before
     * it partition the history instead of sharing a play on the boundary.
     */
    fun bounds(now: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        if (period == StatsPeriod.ALL) return Long.MIN_VALUE..now
        val endOfToday = Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val span = period.dayCount() * TimeUnit.DAYS.toMillis(1)
        val end = endOfToday - offset * span
        return (end - span)..end
    }

    /** What this window is called on screen: `22 – 28 Aug 2026`, or the period's own name. */
    fun label(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (period == StatsPeriod.ALL) return period.label
        val bounds = bounds(now, zone)
        val first = Instant.ofEpochMilli(bounds.first).atZone(zone).toLocalDate()
        // The bound is exclusive: the last day *in* the window is the day before it.
        val last = Instant.ofEpochMilli(bounds.last).atZone(zone).toLocalDate().minusDays(1)
        return if (first.year == last.year && first.month == last.month) {
            "${first.dayOfMonth} – ${last.format(DayMonthYear)}"
        } else {
            "${first.format(DayMonthYear)} – ${last.format(DayMonthYear)}"
        }
    }
}

private val DayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * How many days a period covers. Zero for `ALL`, which covers however long the history is —
 * callers that need a number substitute the span of what they actually found.
 */
internal fun StatsPeriod.dayCount(): Int = when (this) {
    StatsPeriod.WEEK -> 7
    StatsPeriod.MONTH -> 30
    StatsPeriod.YEAR -> 365
    StatsPeriod.ALL -> 0
}

/**
 * A figure, and how it moved against the same-length window before it.
 *
 * [changePercent] is null when there is nothing honest to say: no earlier window was measured, or
 * the earlier one was zero — a percentage against nothing is infinite, not impressive.
 */
@Immutable
data class Trend(val value: Long, val changePercent: Int? = null) {
    companion object {
        fun of(value: Long, previous: Long?): Trend {
            if (previous == null || previous <= 0L) return Trend(value)
            return Trend(value, (((value - previous) * 100.0) / previous).toInt())
        }
    }
}

/** The one song the window was mostly about. */
@Immutable
data class TopSong(
    val trackId: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    /** Null when the source counts plays rather than time — Agro reports its top tracks by play. */
    val seconds: Long?
)

/** The heaviest single day in the window. */
@Immutable
data class BusiestDay(val dayStartMillis: Long, val plays: Trend)

/**
 * Everything the statistics screen draws for one [window].
 *
 * [stats] is the wire shape — the charts and the ranked lists — and the rest is what the screen
 * leads with. The leading figures are separate because they are *comparative*: each carries how it
 * moved since the window before, which the wire shape has no room for and the server does not
 * compute.
 *
 * The comparative half is nullable throughout on purpose. With an Agro server paired the figures
 * come back for a fixed period and cannot be asked for an arbitrary window, so there is no earlier
 * window to compare against and no per-day breakdown of plays. A tile with nothing behind it is
 * not drawn, rather than drawn with a zero in it.
 */
@Immutable
data class ListeningReport(
    val window: StatsWindow,
    val stats: AgroStats,
    /** True when the figures cover every device on the Agro account rather than this one. */
    val isFleetWide: Boolean,
    val topSong: TopSong?,
    val songsPlayed: Trend,
    val listenedSeconds: Trend,
    val playsPerDay: Trend,
    val busiestDay: BusiestDay?,
    val artists: Trend?
) {
    /**
     * Whether the arrows either side of the date range do anything.
     *
     * Only the local history can be sliced to an arbitrary window; Agro answers for a period and
     * nothing else. Offering arrows that cannot move is worse than not offering them.
     */
    val isPageable: Boolean get() = !isFleetWide && window.isPageable
}
