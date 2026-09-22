package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What friends have been into, and how the circle looks together.
 *
 * Both are derived on the server from plays rather than stored, so an empty answer is the normal
 * one for a circle whose members have not opened the relevant switch. Neither is an error worth
 * surfacing: `showActivity` gates the feed, `showStats` gates the recap, and both default closed.
 */
@Singleton
internal class AgroFeedApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {
    suspend fun friendActivity(days: Int = 14, limit: Int = 60): Result<List<AgroFeedItem>> =
        graphQl.execute(
            """
            query Feed(${'$'}days: Int, ${'$'}limit: Int) {
                friendActivity(days: ${'$'}days, limit: ${'$'}limit) { $FEED_FIELDS }
            }
            """.trimIndent(),
            buildJsonObject {
                put("days", days)
                put("limit", limit)
            }
        ).map { data ->
            (data["friendActivity"] as? JsonArray).orEmpty().map { it.jsonObject.toFeedItem() }
        }

    /**
     * The circle's recap.
     *
     * [year] overrides [period] with a calendar year, which is what Agro Replay asks for. The
     * rolling `"YEAR"` period cannot serve it: that means the last 365 days, so a recap of last
     * year opened in February would be half about this one.
     */
    suspend fun recap(
        period: String = "MONTH",
        year: Int? = null,
        utcOffsetMinutes: Int? = null
    ): Result<AgroRecap> = graphQl.execute(
        """
        query Recap(${'$'}period: String, ${'$'}year: Int, ${'$'}utcOffsetMinutes: Int) {
            circleRecap(
                period: ${'$'}period, year: ${'$'}year, utcOffsetMinutes: ${'$'}utcOffsetMinutes
            ) {
                period
                members
                anthem { title artist plays byMember { name value } }
                topTracks { name value }
                topArtists { name value }
                trendsetter { username firsts examples }
                matrix { a b score }
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("period", period)
            year?.let { put("year", it) }
            utcOffsetMinutes?.let { put("utcOffsetMinutes", it) }
        }
    ).mapCatching { data ->
        data["circleRecap"]?.jsonObject?.toRecap()
            ?: error("the server returned no recap")
    }
}
