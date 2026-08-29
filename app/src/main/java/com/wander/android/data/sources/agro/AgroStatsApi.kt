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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
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
     * The server is idempotent on the `playUid` each entry carries, so a batch this device is
     * unsure about can simply be sent again — which is what lets the worker retry a timed-out
     * upload without having to know whether it landed. See [playUid].
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
                                put("playUid", playUid(play))
                            }
                        )
                    }
                }
            )
        }
    ).map { data -> data["recordScrobbles"]?.jsonPrimitive?.intOrNull ?: 0 }

    /**
     * A stable name for one play, so the server can recognise a repeat of it.
     *
     * Agro used to deduplicate on (account, artist, title, time), which worked only because it
     * stored the play time to the second. It no longer does — an exact play time reconstructs when
     * someone sleeps, wakes and commutes, so the server rounds it to the hour — and an hour is far
     * too coarse to tell four plays of one track apart. The identity has to travel with the play
     * rather than being inferred from where it landed.
     *
     * Derived from the play's own contents rather than random, because [ScrobbleSyncWorker] retries
     * a batch it could not confirm. A fresh id per attempt would make the second attempt look like
     * new listening and double every play in it.
     *
     * The exact millisecond goes into the digest and never leaves the device, which is the point:
     * the server gets something that tells two plays apart without being told when either happened.
     */
    private fun playUid(play: PendingScrobble): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Length-prefixed, so a title ending in what looks like a separator cannot be rearranged
        // into a different play with the same digest.
        for (field in listOf(play.artist, play.title)) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(bytes.size.toLong()).array())
            digest.update(bytes)
        }
        digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(play.playedAt).array())
        // Halved to 128 bits: far past the point a collision within one account's history is worth
        // thinking about, and it keeps the column small.
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * The account's listening, across every device.
     *
     * [deviceName] narrows it to one device's plays; null is the whole fleet, which is the number
     * that does not exist anywhere else and the reason for centralising this at all.
     */
    /**
     * Statistics for an account — your own by default, or a friend's when [username] names one.
     *
     * The account was hardcoded to this device's own, so a friend's listening was unreachable from
     * the app even once they had opened it. The server decides whether the answer comes back: your
     * own always, a friend's only when their `showStats` switch is on.
     */
    suspend fun listeningStats(
        period: StatsPeriod,
        deviceName: String? = null,
        username: String? = null
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
            put("userId", username?.takeIf { it.isNotBlank() } ?: graphQl.userId)
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
