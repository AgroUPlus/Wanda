package com.wander.android.data.sources.agro

import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library half of the Agro API: what this device holds, and what it is missing.
 *
 * Metadata only. Moving actual bytes is [AgroUploader]'s job, over REST.
 */
@Singleton
class AgroLibraryApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {

    /**
     * Tells the server what this device holds.
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
                        track.streamUri?.let { put("localRef", it) }
                    })
                }
            })
        }

        return graphQl.execute(mutation, variables).map { data ->
            data["reportHoldings"]?.jsonPrimitive?.int ?: 0
        }
    }

    /**
     * Tells the server this device now holds a track it has just fetched.
     */
    suspend fun reportFetchedHolding(track: MissingTrack, localRef: String?): Result<Int> {
        val mutation = """
            mutation ReportHoldings(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}tracks: [HoldingInput!]!) {
              reportHoldings(userId: ${'$'}userId, deviceId: ${'$'}deviceId, tracks: ${'$'}tracks)
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("tracks", buildJsonArray {
                add(buildJsonObject {
                    put("contentHash", track.contentHash)
                    put("title", track.title)
                    put("artist", track.artist)
                    track.album?.let { put("album", it) }
                    put("durationMs", track.durationMs)
                    put("sizeBytes", track.sizeBytes)
                    track.format?.let { put("format", it) }
                    localRef?.let { put("localRef", it) }
                })
            })
        }

        return graphQl.execute(mutation, variables).map { data ->
            data["reportHoldings"]?.jsonPrimitive?.int ?: 0
        }
    }

    /**
     * Every hash the server believes this device is holding.
     */
    suspend fun deviceHoldings(): Result<List<String>> {
        val query = """
            query DeviceHoldings(${'$'}userId: String!, ${'$'}deviceId: String!) {
              deviceHoldings(userId: ${'$'}userId, deviceId: ${'$'}deviceId)
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
        }
        return graphQl.execute(query, variables).map { data ->
            data["deviceHoldings"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
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
     */
    suspend fun missingOnDevice(limit: Int = 50): Result<List<MissingTrack>> {
        val query = """
            query Missing(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}limit: Int) {
                missingOnDevice(userId: ${'$'}userId, deviceId: ${'$'}deviceId, limit: ${'$'}limit) {
                contentHash title artist album durationMs sizeBytes format
                peerSources { deviceId petname lanAddress isOnline isServerArchive }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", graphQl.userId)
            put("deviceId", graphQl.deviceId)
            put("limit", limit)
        }
        return graphQl.execute(query, variables).map { data ->
            (data["missingOnDevice"]?.jsonArray ?: emptyList()).mapNotNull(::parseMissingTrack)
        }
    }

    /**
     * Files this device holds that the server has already filed away.
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
            (data["reclaimable"]?.jsonArray ?: emptyList()).mapNotNull(::parseMissingTrack)
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
                else -> SyncMode.PEER_TO_PEER
            }
        }
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

    /**
     * Storage used against the account's quota.
     */
    suspend fun storageUsage(): Result<StorageUsage> {
        val query = """
            query Usage(${'$'}userId: String!) {
              storageUsage(userId: ${'$'}userId) {
                usedBytes quotaBytes
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("userId", graphQl.userId) }
        return graphQl.execute(query, variables).mapCatching { data ->
            val obj = data["storageUsage"]?.jsonObject
                ?: error("the server returned no storage usage")
            StorageUsage(
                usedBytes = obj["usedBytes"]?.jsonPrimitive?.long ?: 0L,
                quotaBytes = obj["quotaBytes"]?.jsonPrimitive?.longOrNull
            )
        }
    }

    /** Whether this device is paired and has library sync switched on. */
    val isEnabled: Boolean
        get() = graphQl.isConfigured && secureStorage.agroLibrarySync
}
