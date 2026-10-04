package com.wander.android.data.sources.agro

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** One track a jam played, with the reaction the room gave it. */
@Serializable
internal data class JamRecapTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val trackUri: String = "",
    val addedBy: String,
    val durationMs: Long = 0L,
    val approvals: Long = 0L,
    val skipVotes: Long = 0L
)

@Serializable
internal data class JamRecapContributor(val username: String, val tracks: Long)

/**
 * What a jam was, as the server wrote it down when this account left it.
 *
 * `@Serializable` rather than read field by field like the rest of the Agro API: the same shape is
 * kept in Room as one JSON column, and one definition for both is what keeps them from drifting.
 */
@Serializable
internal data class JamRecap(
    val startedAt: String,
    val endedAt: String,
    val durationMs: Long = 0L,
    val people: List<String> = emptyList(),
    val tracks: List<JamRecapTrack> = emptyList(),
    val tracksOmitted: Long = 0L,
    val topContributor: JamRecapContributor? = null,
    val mostLoved: JamRecapTrack? = null,
    val mostSkipped: JamRecapTrack? = null
)

internal data class StoredJamRecap(val id: String, val createdAt: String, val recap: JamRecap)

/** Tolerant of fields a newer server adds, as every Agro read is (see `AgroJson`). */
internal val JamRecapJson = Json { ignoreUnknownKeys = true }

/** Recaps of jams this account has left. Readable only by the account they were written for. */
@Singleton
internal class AgroJamRecapApi @Inject constructor(private val graphQl: AgroGraphQl) {

    private val trackFields = "title artist artworkUrl trackUri addedBy durationMs approvals skipVotes"

    suspend fun recaps(): Result<List<StoredJamRecap>> = graphQl.execute(
        """
        query { jamRecaps { id createdAt recap {
            startedAt endedAt durationMs people tracksOmitted
            tracks { $trackFields }
            topContributor { username tracks }
            mostLoved { $trackFields }
            mostSkipped { $trackFields }
        } } }
        """.trimIndent(),
        buildJsonObject { }
    ).mapCatching { data ->
        data.objects("jamRecaps").map { entry ->
            StoredJamRecap(
                id = entry.str("id") ?: error("A jam recap arrived without an id"),
                createdAt = entry.str("createdAt").orEmpty(),
                recap = JamRecapJson.decodeFromJsonElement<JamRecap>(
                    entry.obj("recap") ?: error("A jam recap arrived without its contents")
                )
            )
        }
    }

    /** `false` when the server had no such recap for this account — already dismissed elsewhere. */
    suspend fun dismiss(id: String): Result<Boolean> = graphQl.execute(
        """mutation Dismiss(${'$'}id: String!) { dismissJamRecap(id: ${'$'}id) }""",
        buildJsonObject { put("id", id) }
    ).map { data: JsonObject -> data["dismissJamRecap"]?.jsonPrimitive?.booleanOrNull ?: false }
}
