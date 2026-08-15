package com.wander.android.data.sources.agro

import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** A track another device holds that this one does not. */
data class MissingTrack(
    val contentHash: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    /** Container as the server indexed it — "flac", "mp3", "m4a". Null when it never learned. */
    val format: String? = null
)

/**
 * How this deployment moves music between devices, as the server sees it.
 *
 * Both clients ask the same question and act on the same answer; this used to be inferred
 * separately on each device from local settings that knew nothing about the server.
 */
enum class SyncMode {
    /** Streamable from Navidrome: a local copy is a convenience, not the only way to hear it. */
    NAVIDROME,

    /** The server keeps the files but nothing streams them, so a missing track is offered. */
    PEER_TO_PEER,

    /** Index only. The server is not a durable copy, so it never suggests deleting one. */
    INDEX_ONLY;

    /** Whether a device without a track should be offered the bytes. */
    val offersDownloads: Boolean get() = this != NAVIDROME

    /** Whether a redundant local copy is safe to suggest removing. */
    val offersReclaim: Boolean get() = this == NAVIDROME
}

data class LibraryStats(
    val trackCount: Int,
    val archivedCount: Int,
    val totalBytes: Long,
    val spoolBytes: Long
)

/**
 * The library half of the Agro API: what this device holds, and what it is missing.
 *
 * Metadata only. Moving actual bytes is [AgroUploader]'s job, over REST — a multi-megabyte file
 * base64'd through a GraphQL envelope would be both larger and unstreamable.
 */
@Singleton
class AgroLibraryApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {

    /**
     * Tells the server what this device holds.
     *
     * Idempotent and batched: re-sending a track already reported is a no-op server-side, so a
     * client can send everything once and only deltas afterwards without risking divergence.
     * Metadata travels with each entry so the server can index a file it has never been sent —
     * which is what lets the diff work at all in index-only mode.
     */
    suspend fun reportHoldings(tracks: List<TrackEntity>): Result<Int> {
        if (tracks.isEmpty()) return Result.success(0)

        val mutation = """
            mutation ReportHoldings(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}tracks: [HoldingInput!]!) {
              reportHoldings(userId: ${'$'}userId, deviceId: ${'$'}deviceId, tracks: ${'$'}tracks)
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("tracks", buildJsonArray {
                tracks.forEach { track ->
                    val hash = track.contentHash ?: return@forEach
                    add(buildJsonObject {
                        put("contentHash", hash)
                        put("title", track.title)
                        put("artist", track.artist)
                        track.album?.let { put("album", it) }
                        track.albumArtist?.let { put("albumArtist", it) }
                        track.trackNumber?.let { put("trackNo", it) }
                        track.discNumber?.let { put("discNo", it) }
                        track.year?.let { put("year", it) }
                        track.genre?.let { put("genre", it) }
                        put("durationMs", track.durationMs)
                        put("sizeBytes", track.sizeBytes ?: 0L)
                        track.format?.let { put("format", it) }
                        track.bitRateKbps?.let { put("bitrateKbps", it) }
                        // The content URI, so this device can find its own copy again. Opaque to
                        // the server, which never interprets it.
                        track.streamUri?.let { put("localRef", it) }
                    })
                }
            })
        }

        return graphQl.execute(mutation, variables).map { data ->
            data["reportHoldings"]?.jsonPrimitive?.int ?: 0
        }
    }

    /** Forgets holdings this device no longer has — deleted locally, or moved to the server. */
    suspend fun forgetHoldings(hashes: List<String>): Result<Int> {
        if (hashes.isEmpty()) return Result.success(0)
        val mutation = """
            mutation ForgetHoldings(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}hashes: [String!]!) {
              forgetHoldings(userId: ${'$'}userId, deviceId: ${'$'}deviceId, hashes: ${'$'}hashes)
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("hashes", buildJsonArray { hashes.forEach { add(it) } })
        }
        return graphQl.execute(mutation, variables).map { data ->
            data["forgetHoldings"]?.jsonPrimitive?.int ?: 0
        }
    }

    /**
     * What another of this account's devices has that this one does not.
     *
     * The server decides, matching on the recording rather than the bytes — so a different rip of
     * a song already held here does not come back. That comparison deliberately lives on the
     * server: it sees every device's holdings, and one implementation cannot drift from another.
     */
    suspend fun missingOnDevice(limit: Int = 50): Result<List<MissingTrack>> {
        val query = """
            query Missing(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}limit: Int) {
              missingOnDevice(userId: ${'$'}userId, deviceId: ${'$'}deviceId, limit: ${'$'}limit) {
                contentHash title artist album durationMs sizeBytes format
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("limit", limit)
        }
        return graphQl.execute(query, variables).map { data ->
            (data["missingOnDevice"]?.jsonArray ?: emptyList()).mapNotNull(::parseTrack)
        }
    }

    /**
     * Files this device holds that the server has already filed away.
     *
     * The server checks its own disk before answering, so this is stronger than "our index says we
     * uploaded it once" — which is the difference between freeing space and losing a track.
     */
    suspend fun reclaimable(limit: Int = 50): Result<List<MissingTrack>> {
        val query = """
            query Reclaimable(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}limit: Int) {
              reclaimable(userId: ${'$'}userId, deviceId: ${'$'}deviceId, limit: ${'$'}limit) {
                contentHash title artist album durationMs sizeBytes format
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("limit", limit)
        }
        return graphQl.execute(query, variables).map { data ->
            (data["reclaimable"]?.jsonArray ?: emptyList()).mapNotNull(::parseTrack)
        }
    }

    /** Asks the server how this account is meant to sync. */
    suspend fun syncMode(): Result<SyncMode> {
        val query = """
            query Mode(${'$'}userId: String!) { syncMode(userId: ${'$'}userId) }
        """.trimIndent()
        val variables = buildJsonObject { put("userId", graphQl.userId) }
        return graphQl.execute(query, variables).map { data ->
            when (data["syncMode"]?.jsonPrimitive?.contentOrNull) {
                "NAVIDROME" -> SyncMode.NAVIDROME
                "INDEX_ONLY" -> SyncMode.INDEX_ONLY
                // An unrecognised mode from a newer server reads as peer-to-peer: it offers files
                // and never suggests deleting one, which is the safe way to be wrong.
                else -> SyncMode.PEER_TO_PEER
            }
        }
    }

    private fun parseTrack(element: kotlinx.serialization.json.JsonElement): MissingTrack? {
        val obj = element as? JsonObject ?: return null
        return MissingTrack(
            contentHash = obj["contentHash"]?.jsonPrimitive?.contentOrNull ?: return null,
            title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artist = obj["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            album = obj["album"]?.jsonPrimitive?.contentOrNull,
            durationMs = obj["durationMs"]?.jsonPrimitive?.long ?: 0L,
            sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.long ?: 0L,
            format = obj["format"]?.jsonPrimitive?.contentOrNull
        )
    }

    suspend fun stats(): Result<LibraryStats> {
        val query = """
            query Stats(${'$'}userId: String!) {
              libraryStats(userId: ${'$'}userId) {
                trackCount archivedCount totalBytes spoolBytes
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("userId", graphQl.userId) }
        return graphQl.execute(query, variables).mapCatching { data ->
            val obj = data["libraryStats"]?.jsonObject
                ?: error("the server returned no library stats")
            LibraryStats(
                trackCount = obj["trackCount"]?.jsonPrimitive?.int ?: 0,
                archivedCount = obj["archivedCount"]?.jsonPrimitive?.int ?: 0,
                totalBytes = obj["totalBytes"]?.jsonPrimitive?.long ?: 0L,
                spoolBytes = obj["spoolBytes"]?.jsonPrimitive?.long ?: 0L
            )
        }
    }

    /** Whether this device is paired and has library sync switched on. */
    val isEnabled: Boolean
        get() = graphQl.isConfigured && secureStorage.agroLibrarySync
}
