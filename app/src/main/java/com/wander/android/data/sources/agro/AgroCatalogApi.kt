package com.wander.android.data.sources.agro

import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
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
 *
 * ## Talking to a server older than this client
 *
 * Two fields here — `lyrics` and `lyricsSource` — and the batch mutation postdate the first
 * servers. What the server can take is learned once, at registration, and kept in [SecureStorage];
 * see [AgroClient.registerNode]. This class asks that rather than discovering it per request,
 * because the version that did discover it per request sent every publish twice on any failure at
 * all, which turned one rejected token into two rejected tokens and reported the second.
 */
@Singleton
internal class AgroCatalogApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage
) {
    companion object {
        /** The server takes lyrics alongside the fingerprint. */
        const val CAPABILITY_LYRICS = "catalog.lyrics"

        /** ...and records what supplied them. */
        const val CAPABILITY_LYRICS_SOURCE = "catalog.lyricsSource"

        /** `publishRecordings` exists, so a run need not be one request per recording. */
        const val CAPABILITY_BATCH = "catalog.batchPublish"

        /** Entries the server accepts in one batch. Matches its own cap. */
        const val MAX_BATCH = 25
    }

    /** One recording on its way to the catalogue. */
    data class Publication(
        val embeddingHex: String,
        val dim: Int,
        val model: String,
        val version: Int,
        val durationMs: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val sourceUri: String?,
        val lyrics: String? = null,
        val lyricsSource: String? = null
    )

    val supportsBatch: Boolean get() = secureStorage.serverSupports(CAPABILITY_BATCH)

    /**
     * Publishes one embedding, and answers with the recording the server filed it under.
     *
     * That id may be one another device created — which is the point, and how two encodings of
     * one performance stop being two recordings.
     */
    suspend fun publish(publication: Publication): Result<String> {
        val sendLyrics = !publication.lyrics.isNullOrBlank() &&
            secureStorage.serverSupports(CAPABILITY_LYRICS)
        val sendSource = sendLyrics && !publication.lyricsSource.isNullOrBlank() &&
            secureStorage.serverSupports(CAPABILITY_LYRICS_SOURCE)

        val arguments = buildString {
            append("embedding: ${'$'}embedding, dim: ${'$'}dim, model: ${'$'}model, ")
            append("version: ${'$'}version, durationMs: ${'$'}durationMs, ")
            append("title: ${'$'}title, artist: ${'$'}artist, album: ${'$'}album, ")
            append("sourceUri: ${'$'}sourceUri")
            if (sendLyrics) append(", lyrics: ${'$'}lyrics")
            if (sendSource) append(", lyricsSource: ${'$'}lyricsSource")
        }
        val declarations = buildString {
            append("${'$'}embedding: String!, ${'$'}dim: Int!, ${'$'}model: String!, ")
            append("${'$'}version: Int!, ${'$'}durationMs: Int!, ")
            append("${'$'}title: String, ${'$'}artist: String, ${'$'}album: String, ")
            append("${'$'}sourceUri: String")
            if (sendLyrics) append(", ${'$'}lyrics: String")
            if (sendSource) append(", ${'$'}lyricsSource: String")
        }

        return graphQl.execute(
            "mutation PublishRecording($declarations) { publishRecording($arguments) }",
            buildJsonObject {
                put("embedding", publication.embeddingHex)
                put("dim", publication.dim)
                put("model", publication.model)
                put("version", publication.version)
                put("durationMs", publication.durationMs)
                put("title", publication.title)
                put("artist", publication.artist)
                put("album", publication.album)
                put("sourceUri", publication.sourceUri)
                if (sendLyrics) put("lyrics", publication.lyrics)
                if (sendSource) put("lyricsSource", publication.lyricsSource)
            }
        ).map { data -> data["publishRecording"]?.jsonPrimitive?.contentOrNull.orEmpty() }
    }

    /**
     * Publishes up to [MAX_BATCH] recordings in one request.
     *
     * Answers positionally: the nth result is the nth publication's, and an entry the server
     * refused carries its complaint instead of an id. A refusal is per-entry on purpose — one
     * malformed embedding should cost the client that recording, not the whole run.
     *
     * Only call this when [supportsBatch]; an older server has no such mutation and will reject
     * the request outright.
     */
    suspend fun publishAll(publications: List<Publication>): Result<List<PublishOutcome>> {
        if (publications.isEmpty()) return Result.success(emptyList())

        val sendLyrics = secureStorage.serverSupports(CAPABILITY_LYRICS)
        val sendSource = sendLyrics && secureStorage.serverSupports(CAPABILITY_LYRICS_SOURCE)

        val entries = buildJsonArray {
            publications.forEach { publication ->
                add(
                    buildJsonObject {
                        put("embedding", publication.embeddingHex)
                        put("dim", publication.dim)
                        put("model", publication.model)
                        put("version", publication.version)
                        put("durationMs", publication.durationMs)
                        put("title", publication.title)
                        put("artist", publication.artist)
                        put("album", publication.album)
                        put("sourceUri", publication.sourceUri)
                        if (sendLyrics && !publication.lyrics.isNullOrBlank()) {
                            put("lyrics", publication.lyrics)
                            if (sendSource && !publication.lyricsSource.isNullOrBlank()) {
                                put("lyricsSource", publication.lyricsSource)
                            }
                        }
                    }
                )
            }
        }

        return graphQl.execute(
            """
            mutation PublishRecordings(${'$'}entries: [PublishRecordingInput!]!) {
                publishRecordings(entries: ${'$'}entries) { recordingId error }
            }
            """.trimIndent(),
            buildJsonObject { put("entries", entries) }
        ).map { data ->
            (data["publishRecordings"] as? JsonArray)
                ?.mapNotNull { element ->
                    val entry = element as? JsonObject ?: return@mapNotNull null
                    PublishOutcome(
                        recordingId = entry["recordingId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        error = entry["error"]?.jsonPrimitive?.contentOrNull
                    )
                }
                .orEmpty()
        }
    }

    /** What became of one entry of a batch. */
    data class PublishOutcome(val recordingId: String, val error: String?)

    /**
     * Everything published after [since], oldest first.
     *
     * The caller keeps the highest `updatedAt` it has seen and passes it back. Re-reading an entry
     * it already holds is harmless — these are facts about audio, so seeing one twice is agreeing
     * with itself.
     */
    suspend fun since(since: Long, limit: Int = 200): Result<List<AgroCatalogEntry>> {
        val fields = buildString {
            append("recordingId embedding dim model version durationMs title artist album")
            if (secureStorage.serverSupports(CAPABILITY_LYRICS)) append(" lyrics")
            if (secureStorage.serverSupports(CAPABILITY_LYRICS_SOURCE)) append(" lyricsSource")
            append(" sources updatedAt")
        }

        return graphQl.execute(
            """
            query CatalogSince(${'$'}since: Int!, ${'$'}limit: Int!) {
                catalogSince(since: ${'$'}since, limit: ${'$'}limit) { $fields }
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
    }

    private fun JsonObject.toCatalogEntry(): AgroCatalogEntry? {
        val id = this["recordingId"]?.jsonPrimitive?.contentOrNull ?: return null
        val embedding = this["embedding"]?.jsonPrimitive?.contentOrNull ?: return null
        return AgroCatalogEntry(
            recordingId = id,
            embeddingHex = embedding,
            dim = this["dim"]?.jsonPrimitive?.intOrNull ?: 0,
            model = this["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            version = this["version"]?.jsonPrimitive?.intOrNull ?: 0,
            durationMs = this["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = this["title"]?.jsonPrimitive?.contentOrNull,
            artist = this["artist"]?.jsonPrimitive?.contentOrNull,
            album = this["album"]?.jsonPrimitive?.contentOrNull,
            lyrics = this["lyrics"]?.jsonPrimitive?.contentOrNull,
            lyricsSource = this["lyricsSource"]?.jsonPrimitive?.contentOrNull,
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
    /** The embedding as hex int8. Unpacked and compared locally rather than trusting the match. */
    val embeddingHex: String,
    val dim: Int,
    /** Which embedder produced it. An entry from a model this device does not run is skipped. */
    val model: String,
    val version: Int,
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String? = null,
    /** What supplied [lyrics] — `LRCLIB`, `Native`. Null from a server that does not record it. */
    val lyricsSource: String? = null,
    /** Namespaced ids known to hold this audio — `ytm:…`, `navidrome:…`. Never a `local:` id. */
    val sources: List<String>,
    val updatedAt: Long
)
