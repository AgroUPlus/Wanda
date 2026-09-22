package com.wander.android.data.sources.agro

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The year in review, as the server sees it.
 *
 * Separate from [AgroStatsApi] even though both read the same plays, because they answer different
 * questions: statistics are a rolling window ending now, and a recap is a *calendar* year. Asking
 * the statistics query for "the last 365 days" in February would give a recap half about this year.
 *
 * Every call takes the listener's offset from UTC. The server buckets hours, days and — the part
 * that actually matters — which year a play belongs to, and all three have to be answered in the
 * listener's own time or a play just before midnight on New Year's Eve lands in the wrong recap.
 */
@Singleton
internal class AgroReplayApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {

    /**
     * The recap for [year].
     *
     * [username] names a friend rather than this account. The server answers for a friend only
     * when they have opened their statistics, and refuses identically for a closed friend and a
     * stranger — so a failure here is never evidence that an account exists.
     */
    suspend fun wrapped(
        year: Int,
        utcOffsetMinutes: Int,
        username: String? = null
    ): Result<AgroWrapped> = graphQl.execute(
        """
        query Wrapped(${'$'}userId: String!, ${'$'}year: Int!, ${'$'}utcOffsetMinutes: Int) {
            agroWrapped(
                userId: ${'$'}userId, year: ${'$'}year, utcOffsetMinutes: ${'$'}utcOffsetMinutes
            ) {
                year totalMinutes totalPlays
                topArtists { name value }
                topTracks { name value }
                topAlbums { name value }
                topGenres { name value }
                topHourLocal longestStreakDays activeDaysCount
                newArtistsCount totalArtists
                byMonth byHour
                byDevice { name value }
                firstPlay lastPlay
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", username?.takeIf { it.isNotBlank() } ?: graphQl.userId)
            put("year", year)
            put("utcOffsetMinutes", utcOffsetMinutes)
        }
    ).mapCatching { data ->
        val wrapped = data["agroWrapped"]?.jsonObject ?: error("Agro returned no recap")
        AgroWrapped(
            year = wrapped["year"]?.jsonPrimitive?.intOrNull ?: year,
            totalMinutes = wrapped.long("totalMinutes"),
            totalPlays = wrapped.long("totalPlays"),
            topArtists = wrapped.entries("topArtists"),
            topTracks = wrapped.entries("topTracks"),
            topAlbums = wrapped.entries("topAlbums"),
            topGenres = wrapped.entries("topGenres"),
            topHourLocal = wrapped["topHourLocal"]?.jsonPrimitive?.intOrNull,
            longestStreakDays = wrapped.long("longestStreakDays").toInt(),
            activeDaysCount = wrapped.long("activeDaysCount").toInt(),
            newArtistsCount = wrapped.long("newArtistsCount").toInt(),
            totalArtists = wrapped.long("totalArtists").toInt(),
            byMonth = wrapped.longs("byMonth"),
            byHour = wrapped.longs("byHour"),
            byDevice = wrapped.entries("byDevice"),
            firstPlay = wrapped.str("firstPlay"),
            lastPlay = wrapped.str("lastPlay")
        )
    }

    /**
     * This account's standing among the server's other listeners for [year].
     *
     * Takes no username, and cannot: the server offers no way to ask this about somebody else.
     */
    suspend fun chartStanding(
        year: Int,
        utcOffsetMinutes: Int
    ): Result<AgroChartStanding> = graphQl.execute(
        """
        query ChartStanding(${'$'}year: Int!, ${'$'}utcOffsetMinutes: Int) {
            replayChartStanding(year: ${'$'}year, utcOffsetMinutes: ${'$'}utcOffsetMinutes) {
                percentile cohortSize minutes suppressed
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("year", year)
            put("utcOffsetMinutes", utcOffsetMinutes)
        }
    ).mapCatching { data ->
        val standing = data["replayChartStanding"]?.jsonObject
            ?: error("Agro returned no chart standing")
        AgroChartStanding(
            percentile = standing["percentile"]?.jsonPrimitive?.int ?: 0,
            cohortSize = standing["cohortSize"]?.jsonPrimitive?.int ?: 0,
            minutes = standing["minutes"]?.jsonPrimitive?.int ?: 0,
            suppressed = standing.bool("suppressed")
        )
    }
}
