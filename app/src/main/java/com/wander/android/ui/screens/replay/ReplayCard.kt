package com.wander.android.ui.screens.replay

import androidx.compose.runtime.Immutable
import com.wander.android.data.replay.ReplayCircle
import com.wander.android.data.replay.ReplayEntry
import com.wander.android.data.replay.ReplayReport

/**
 * One screen of the story.
 *
 * A card exists only when there is something behind it. That is why this is a sealed hierarchy of
 * *already-resolved* values rather than a list of enum tags the cards look data up from: building
 * the deck is the single place that decides whether a card has anything to say, so no card has to
 * render a defensive "no data" state, and the progress bar is built from what will actually be
 * shown rather than from what might be.
 */
@Immutable
sealed interface ReplayCard {

    @Immutable
    data class Intro(val year: Int, val isFleetWide: Boolean) : ReplayCard

    @Immutable
    data class Minutes(val minutes: Long, val plays: Long) : ReplayCard

    /** Twelve months of plays, and the heaviest one. */
    @Immutable
    data class Shape(val byMonth: List<Long>, val peakMonth: Int) : ReplayCard

    @Immutable
    data class Hours(val byHour: List<Long>, val peakHour: Int) : ReplayCard

    @Immutable
    data class TopArtists(val artists: List<ReplayEntry>, val totalArtists: Int) : ReplayCard

    @Immutable
    data class TopSong(val title: String, val artist: String, val plays: Long) : ReplayCard

    @Immutable
    data class Genres(val genres: List<ReplayEntry>) : ReplayCard

    @Immutable
    data class Discovery(val newArtists: Int, val names: List<String>) : ReplayCard

    @Immutable
    data class Streak(val longestStreakDays: Int, val activeDays: Int) : ReplayCard

    @Immutable
    data class Devices(val devices: List<ReplayEntry>) : ReplayCard

    @Immutable
    data class Charts(val percentile: Int, val cohortSize: Int) : ReplayCard

    @Immutable
    data class Circle(val circle: ReplayCircle) : ReplayCard

    /** Nothing was played. One card, honestly, instead of eleven empty ones. */
    @Immutable
    data class Silence(val year: Int) : ReplayCard

    @Immutable
    data class Outro(val report: ReplayReport) : ReplayCard
}

/**
 * Turns a year into the story it can actually tell.
 *
 * Pure, so `ReplayDeckTest` can assert what a sparse year drops without touching Compose.
 *
 * Nothing here substitutes a zero to keep a card on screen. A phone with no server paired has no
 * devices card, a server too small to rank has no charts card, and somebody with no friends on it
 * has no circle card — and the story is shorter rather than padded.
 */
fun buildReplayDeck(report: ReplayReport): List<ReplayCard> {
    if (report.isEmpty) {
        return listOf(ReplayCard.Silence(report.year), ReplayCard.Outro(report))
    }

    val cards = mutableListOf<ReplayCard>(
        ReplayCard.Intro(report.year, report.isFleetWide),
        ReplayCard.Minutes(report.totalMinutes, report.totalPlays)
    )

    report.byMonth.peakIndex()?.let { cards += ReplayCard.Shape(report.byMonth, it) }
    report.topHourLocal?.let { cards += ReplayCard.Hours(report.byHour, it) }

    if (report.topArtists.isNotEmpty()) {
        cards += ReplayCard.TopArtists(report.topArtists.take(TOP_ARTISTS), report.totalArtists)
    }
    report.topTracks.firstOrNull()?.let { top ->
        val (title, artist) = splitJoinedName(top.name)
        cards += ReplayCard.TopSong(title, artist, top.value)
    }
    if (report.topGenres.isNotEmpty()) {
        cards += ReplayCard.Genres(report.topGenres.take(TOP_GENRES))
    }
    if (report.newArtistsCount > 0) {
        // Named from the top artists, which is where the ones worth naming are. A new artist
        // played once is true but not a headline.
        cards += ReplayCard.Discovery(
            newArtists = report.newArtistsCount,
            names = report.topArtists.take(TOP_DISCOVERIES).map { it.name }
        )
    }
    if (report.longestStreakDays > 1) {
        cards += ReplayCard.Streak(report.longestStreakDays, report.activeDaysCount)
    }
    // One device is not a breakdown, and the local path reports none at all.
    if (report.byDevice.size > 1) {
        cards += ReplayCard.Devices(report.byDevice)
    }
    report.chartPercentile?.let { percentile ->
        cards += ReplayCard.Charts(percentile, report.chartCohortSize)
    }
    report.circle?.takeIf { it.isWorthShowing }?.let { cards += ReplayCard.Circle(it) }

    cards += ReplayCard.Outro(report)
    return cards
}

/**
 * Splits `Title — Artist` back apart.
 *
 * `lastIndexOf` rather than `indexOf`, for the reason `StatsRepository` already gives: a title
 * containing an em dash is a real title, and an artist containing one is rarer than a song called
 * `Sunset — Reprise`.
 */
internal fun splitJoinedName(name: String): Pair<String, String> {
    val split = name.lastIndexOf(NAME_SEPARATOR)
    if (split <= 0) return name to ""
    return name.take(split) to name.substring(split + NAME_SEPARATOR.length)
}

/** The busiest index, or null when nothing was played — never index zero by default. */
private fun List<Long>.peakIndex(): Int? =
    withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index

private const val NAME_SEPARATOR = " — "
private const val TOP_ARTISTS = 5
private const val TOP_GENRES = 5
private const val TOP_DISCOVERIES = 3
