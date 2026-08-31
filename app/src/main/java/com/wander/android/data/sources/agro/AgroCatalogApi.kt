package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared fingerprint catalogue.
 *
 * Everything the app does with fingerprints works without this: identification runs on device
 * against a local index. The catalogue only means the work is done once for everyone instead of
 * once per device, and that a source with poor tags can inherit what a source with good ones
 * supplied for the same audio.
 */
@Singleton
internal class AgroCatalogApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {

    /**
     * Publishes one fingerprint, and answers with the recording the server filed it under.
     *
     * That id may be one another device created — which is the point, and how two encodings of
     * one performance stop being two recordings.
     */
    suspend fun publish(
        subHashesHex: String,
        durationMs: Long,
        title: String?,
        artist: String?,
        album: String?,
        sourceUri: String?
    ): Result<String> = graphQl.execute(
        """
        mutation PublishRecording(
            ${'$'}subHashes: String!, ${'$'}durationMs: Int!,
            ${'$'}title: String, ${'$'}artist: String, ${'$'}album: String, ${'$'}sourceUri: String
        ) {
            publishRecording(
                subHashes: ${'$'}subHashes, durationMs: ${'$'}durationMs,
                title: ${'$'}title, artist: ${'$'}artist, album: ${'$'}album, sourceUri: ${'$'}sourceUri
            )
        }
        """.trimIndent(),
        buildJsonObject {
            put("subHashes", subHashesHex)
            put("durationMs", durationMs)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("sourceUri", sourceUri)
        }
    ).map { data -> data["publishRecording"]?.jsonPrimitive?.contentOrNull.orEmpty() }

    /**
     * Everything published after [since], oldest first.
     *
     * The caller keeps the highest `updatedAt` it has seen and passes it back. Re-reading an entry
     * it already holds is harmless — these are facts about audio, so seeing one twice is agreeing
     * with itself.
     */
    suspend fun since(since: Long, limit: Int = 200): Result<List<AgroCatalogEntry>> = graphQl.execute(
        """
        query CatalogSince(${'$'}since: Int!, ${'$'}limit: Int!) {
            catalogSince(since: ${'$'}since, limit: ${'$'}limit) {
                recordingId subHashes durationMs title artist album sources updatedAt
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("since", since)
            put("limit", limit)
        }
    ).map { data ->
        (data["catalogSince"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.toCatalogEntry() }
            .orEmpty()
    }

    private fun JsonObject.toCatalogEntry(): AgroCatalogEntry? {
        val id = this["recordingId"]?.jsonPrimitive?.contentOrNull ?: return null
        val hashes = this["subHashes"]?.jsonPrimitive?.contentOrNull ?: return null
        return AgroCatalogEntry(
            recordingId = id,
            subHashesHex = hashes,
            durationMs = this["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = this["title"]?.jsonPrimitive?.contentOrNull,
            artist = this["artist"]?.jsonPrimitive?.contentOrNull,
            album = this["album"]?.jsonPrimitive?.contentOrNull,
            sources = (this["sources"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            updatedAt = this["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }
}

/** One recording as the catalogue knows it. */
internal data class AgroCatalogEntry(
    val recordingId: String,
    val subHashesHex: String,
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** Namespaced ids known to hold this audio — `ytm:…`, `navidrome:…`, `local:…`. */
    val sources: List<String>,
    val updatedAt: Long
)
