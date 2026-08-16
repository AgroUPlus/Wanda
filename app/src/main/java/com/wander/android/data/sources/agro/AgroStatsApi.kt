package com.wander.android.data.sources.agro

import com.wander.android.core.database.dao.PendingScrobble
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening statistics, shared across every device on the Agro account.
 *
 * Both halves live here: reporting what this phone played, and reading back what the whole fleet
 * did. They belong together because they are the same table seen from two ends, and separating them
 * would mean two files that only ever change for the same reason.
 */
@Singleton
class AgroStatsApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {

    /**
     * Reports a batch of plays.
     *
     * The server is idempotent on (account, artist, title, time), so a batch this device is unsure
     * about can simply be sent again — which is what lets the worker retry a timed-out upload
     * without having to know whether it landed.
     */
    suspend fun recordScrobbles(
        deviceName: String,
        plays: List<PendingScrobble>
    ): Result<Int> = graphQl.execute(
        """
        mutation RecordScrobbles(
            ${'$'}userId: String!, ${'$'}deviceName: String!, ${'$'}clientType: String,
            ${'$'}entries: [ScrobbleInput!]!
        ) {
            recordScrobbles(
                userId: ${'$'}userId, deviceName: ${'$'}deviceName, clientType: ${'$'}clientType,
                entries: ${'$'}entries
            )
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceName", deviceName)
            put("clientType", "wanda")
            put(
                "entries",
                buildJsonArray {
                    plays.forEach { play ->
                        add(
                            buildJsonObject {
                                put("trackTitle", play.title)
                                put("artistName", play.artist)
                                put("albumName", play.album)
                                put("genre", play.genre)
                                put("durationSecs", play.durationMs / 1000)
                                // The time the play happened on *this* device. Letting the server
                                // stamp it would pile a week of offline listening onto the
                                // afternoon it was finally uploaded.
                                put("playedAt", Instant.ofEpochMilli(play.playedAt).toString())
                            }
                        )
                    }
                }
            )
        }
    ).map { data -> data["recordScrobbles"]?.jsonPrimitive?.intOrNull ?: 0 }

    /**
     * The account's listening, across every device.
     *
     * [deviceName] narrows it to one device's plays; null is the whole fleet, which is the number
     * that does not exist anywhere else and the reason for centralising this at all.
     */
    suspend fun listeningStats(
        period: StatsPeriod,
        deviceName: String? = null
    ): Result<AgroStats> = graphQl.execute(
        """
        query Stats(${'$'}userId: String!, ${'$'}period: String, ${'$'}deviceName: String) {
            listeningStats(
                userId: ${'$'}userId, period: ${'$'}period, deviceName: ${'$'}deviceName
            ) {
                secsToday secsWeek secsTotal playsTotal streak
                topArtists { name value }
                topAlbums { name value }
                topTracks { name value }
                byDay
                byHour
                byDevice { name value }
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", graphQl.userId)
            put("period", period.wireName)
            put("deviceName", deviceName)
        }
    ).mapCatching { data ->
        val stats = data["listeningStats"]?.jsonObject
            ?: error("Agro returned no statistics")
        AgroStats(
            secondsToday = stats.long("secsToday"),
            secondsWeek = stats.long("secsWeek"),
            secondsTotal = stats.long("secsTotal"),
            playCount = stats.long("playsTotal"),
            streakDays = stats.long("streak").toInt(),
            topArtists = stats.entries("topArtists"),
            topAlbums = stats.entries("topAlbums"),
            topTracks = stats.entries("topTracks"),
            byDay = stats.longs("byDay"),
            byHour = stats.longs("byHour"),
            byDevice = stats.entries("byDevice")
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun kotlinx.serialization.json.JsonObject.longs(key: String): List<Long> =
    this[key]?.jsonArray.orEmpty().map { it.jsonPrimitive.longOrNull ?: 0L }

private fun kotlinx.serialization.json.JsonObject.entries(key: String): List<StatEntry> =
    this[key]?.jsonArray.orEmpty().mapNotNull { element ->
        val entry = element.jsonObject
        val name = entry["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        StatEntry(name, entry["value"]?.jsonPrimitive?.longOrNull ?: 0L)
    }
