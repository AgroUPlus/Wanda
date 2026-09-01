package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the server's other accounts have been playing, and this device's contribution to it.
 *
 * Separate from [AgroStatsApi] even though both are about plays, because they are opposite in the
 * one way that matters: statistics are the account's *own* history, readable and purgeable by the
 * account that made them, while these are contributions to a shared total that nothing can
 * attribute afterwards. The server's table has no account column at all.
 *
 * Nothing here is required for the app to work, and nothing is sent unless the user has turned
 * contribution on.
 */
@Singleton
class AgroPopularityApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {

    /** The fleet's most-played recordings over a rolling window, most played first. */
    suspend fun popularTracks(days: Int = 7, limit: Int = 30): Result<List<AgroPopularTrack>> =
        graphQl.execute(
            """
            query PopularTracks(${'$'}days: Int!, ${'$'}limit: Int!) {
                popularTracks(days: ${'$'}days, limit: ${'$'}limit) {
                    title artist album count
                }
            }
            """.trimIndent(),
            buildJsonObject {
                put("days", days)
                put("limit", limit)
            }
        ).map { data ->
            (data["popularTracks"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.toPopularTrack() }
                .orEmpty()
        }

    /**
     * Adds this device's plays to the shared totals. Answers how many entries were counted.
     *
     * **Not idempotent, and it cannot be.** A repeated submission is indistinguishable from having
     * listened to something twice, which is exactly what carrying no submitter identity means. So a
     * failed request must not be retried with the same batch: the counts are dropped instead, and
     * the shelf is a little less accurate. Trading a few counts for an identifier on the server
     * would be trading the whole point of the feature for its accuracy.
     */
    suspend fun submitPlayCounts(counts: List<AgroPlayCount>): Result<Int> = graphQl.execute(
        """
        mutation SubmitPlayCounts(${'$'}entries: [PlayCountInput!]!) {
            submitPlayCounts(entries: ${'$'}entries)
        }
        """.trimIndent(),
        buildJsonObject {
            put(
                "entries",
                buildJsonArray {
                    counts.forEach { count ->
                        add(
                            buildJsonObject {
                                put("title", count.title)
                                put("artist", count.artist)
                                put("album", count.album)
                                put("count", count.count)
                            }
                        )
                    }
                }
            )
        }
    ).map { data -> data["submitPlayCounts"]?.jsonPrimitive?.int ?: 0 }

    private fun JsonObject.toPopularTrack(): AgroPopularTrack? {
        val title = this["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val artist = this["artist"]?.jsonPrimitive?.contentOrNull ?: return null
        return AgroPopularTrack(
            title = title,
            artist = artist,
            album = this["album"]?.jsonPrimitive?.contentOrNull,
            count = this["count"]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }
}

/** One recording the fleet has been playing. Carries no id: the server holds no rows to point at. */
data class AgroPopularTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val count: Long
)

/** One recording's plays since this device last reported. */
data class AgroPlayCount(
    val title: String,
    val artist: String,
    val album: String?,
    val count: Int
)
