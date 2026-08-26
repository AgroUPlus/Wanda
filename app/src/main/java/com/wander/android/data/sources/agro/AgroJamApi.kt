package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** How a jam decides what plays next. */
internal enum class JamMode { OPEN, DEMOCRACY }

/** One track in the jam — queued, or still waiting on the room. */
internal data class JamTrack(
    val id: String,
    val addedBy: String,
    val trackUri: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    /** Approvals so far, not counting whoever suggested it. */
    val approvals: Long,
    /** Whether *you* have approved it, so the control shows its state rather than guessing. */
    val approved: Boolean,
    /** How many more approvals it needs. Sent by the server so the rule lives in one place. */
    val stillNeeded: Long
)

/**
 * What the whole room is hearing.
 *
 * [positionMs] is worked out by the server from when the track started, which is what lets a device
 * joining late drop straight into the right place rather than starting the song again.
 */
internal data class JamNowPlaying(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val positionMs: Long,
    /** Votes to skip this track, and how many the room needs. */
    val skipVotes: Long,
    val skipsNeeded: Long,
    val youSkipped: Boolean
)

/** A friend's jam, before you are in it. No code, no queue — seeing it is not being in it. */
internal data class FriendJam(
    val id: String,
    val host: String,
    val mode: JamMode,
    val members: List<String>,
    val nowPlayingTitle: String?
)

internal data class Jam(
    val id: String,
    /** What you give someone so they can join. */
    val code: String,
    val host: String,
    val mode: JamMode,
    val isHost: Boolean,
    val members: List<String>,
    /** Accepted tracks, in the order they were added. Excludes whatever is playing. */
    val queue: List<JamTrack>,
    /** Suggestions the room has not accepted. Always empty in [JamMode.OPEN]. */
    val proposals: List<JamTrack>,
    val nowPlaying: JamNowPlaying?,
    val approvalsNeeded: Long,
    /** Whether friends can find this jam, or only somebody holding the code. */
    val openToFriends: Boolean
)

/**
 * The jam session API.
 *
 * Every mutation answers with the whole jam rather than a fragment, so the screen never has to
 * stitch a view together from a mutation result and a query that may already be stale.
 */
@Singleton
internal class AgroJamApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {
    private val trackFields =
        "id addedBy trackUri title artist artworkUrl durationMs approvals approved stillNeeded"

    private val jamFields = """
        id code host mode isHost members approvalsNeeded visibility
        queue { $trackFields }
        proposals { $trackFields }
        nowPlaying {
            trackId title artist artworkUrl durationMs positionMs
            skipVotes skipsNeeded youSkipped
        }
    """.trimIndent()

    suspend fun jam(): Result<Jam?> = graphQl.execute(
        "query { jam { $jamFields } }",
        buildJsonObject { }
    ).map { data -> (data["jam"] as? JsonObject)?.toJam() }

    suspend fun createJam(mode: JamMode): Result<Jam> = graphQl.execute(
        """mutation CreateJam(${'$'}mode: String) { createJam(mode: ${'$'}mode) { $jamFields } }""",
        buildJsonObject { put("mode", mode.name.lowercase()) }
    ).mapCatching { data -> data["createJam"]!!.jsonObject.toJam() }

    suspend fun joinJam(code: String): Result<Jam> = graphQl.execute(
        """mutation JoinJam(${'$'}code: String!) { joinJam(code: ${'$'}code) { $jamFields } }""",
        buildJsonObject { put("code", code.trim().uppercase()) }
    ).mapCatching { data -> data["joinJam"]!!.jsonObject.toJam() }

    suspend fun leaveJam(): Result<Boolean> = graphQl.execute(
        "mutation { leaveJam }",
        buildJsonObject { }
    ).map { data -> data["leaveJam"]?.jsonPrimitive?.booleanOrNull ?: false }

    /**
     * Suggests a track.
     *
     * The duration is not optional in practice: the server advances the room on it, so a track sent
     * without one would be retired the instant it started.
     */
    suspend fun addTrack(track: com.wander.android.data.model.UnifiedTrack): Result<Jam> =
        graphQl.execute(
            """
            mutation AddJamTrack(
                ${'$'}uri: String!, ${'$'}title: String!, ${'$'}artist: String!, ${'$'}art: String,
                ${'$'}duration: Int, ${'$'}isLive: Boolean
            ) {
                addJamTrack(
                    trackUri: ${'$'}uri, title: ${'$'}title, artist: ${'$'}artist,
                    artworkUrl: ${'$'}art, durationMs: ${'$'}duration, isLive: ${'$'}isLive
                ) {
                    $jamFields
                }
            }
            """.trimIndent(),
            buildJsonObject {
                put("uri", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("art", track.artworkUrl)
                put("duration", track.durationMs)
                // Tells the room's clock that this one has no end of its own, so it holds the
                // room until somebody skips instead of being retired on a duration it does not
                // have. Without it a radio looks exactly like a track whose length failed to
                // parse, and those want the opposite treatment.
                put("isLive", track.isLive)
            }
        ).mapCatching { data -> data["addJamTrack"]!!.jsonObject.toJam() }

    /** Accepts somebody's suggestion. One-way: there is no un-approving. */
    suspend fun approve(trackId: String): Result<Jam> = graphQl.execute(
        """mutation Approve(${'$'}id: String!) { approveJamTrack(trackId: ${'$'}id) { $jamFields } }""",
        buildJsonObject { put("id", trackId) }
    ).mapCatching { data -> data["approveJamTrack"]!!.jsonObject.toJam() }

    suspend fun removeTrack(trackId: String): Result<Jam> = graphQl.execute(
        """mutation Remove(${'$'}id: String!) { removeJamTrack(trackId: ${'$'}id) { $jamFields } }""",
        buildJsonObject { put("id", trackId) }
    ).mapCatching { data -> data["removeJamTrack"]!!.jsonObject.toJam() }

    /** Votes to skip whatever is playing. A majority of the room retires it at once. */
    suspend fun voteSkip(): Result<Jam> = graphQl.execute(
        "mutation { voteSkipJamTrack { $jamFields } }",
        buildJsonObject { }
    ).mapCatching { data -> data["voteSkipJamTrack"]!!.jsonObject.toJam() }

    /** Jams friends have opened up, so one can be joined without a code. */
    suspend fun friendJams(): Result<List<FriendJam>> = graphQl.execute(
        "query { friendJams { id host mode members nowPlayingTitle } }",
        buildJsonObject { }
    ).map { data ->
        data["friendJams"]?.jsonArray?.map { entry ->
            val jam = entry.jsonObject
            FriendJam(
                id = jam.text("id"),
                host = jam.text("host"),
                mode = if (jam.text("mode").equals("open", ignoreCase = true)) {
                    JamMode.OPEN
                } else {
                    JamMode.DEMOCRACY
                },
                members = jam["members"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                nowPlayingTitle = jam["nowPlayingTitle"]?.jsonPrimitive?.contentOrNull
            )
        }.orEmpty()
    }

    suspend fun joinFriendJam(jamId: String): Result<Jam> = graphQl.execute(
        """mutation JoinOpen(${'$'}id: String!) { joinFriendJam(jamId: ${'$'}id) { $jamFields } }""",
        buildJsonObject { put("id", jamId) }
    ).mapCatching { data -> data["joinFriendJam"]!!.jsonObject.toJam() }

    /** Opens the jam to friends, or shuts it back to code-only. Creator only. */
    suspend fun setVisibility(openToFriends: Boolean): Result<Jam> = graphQl.execute(
        """mutation Vis(${'$'}v: String!) { setJamVisibility(visibility: ${'$'}v) { $jamFields } }""",
        buildJsonObject { put("v", if (openToFriends) "friends" else "code") }
    ).mapCatching { data -> data["setJamVisibility"]!!.jsonObject.toJam() }

    suspend fun setMode(mode: JamMode): Result<Jam> = graphQl.execute(
        """mutation SetMode(${'$'}mode: String!) { setJamMode(mode: ${'$'}mode) { $jamFields } }""",
        buildJsonObject { put("mode", mode.name.lowercase()) }
    ).mapCatching { data -> data["setJamMode"]!!.jsonObject.toJam() }

}

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.toJam(): Jam = Jam(
    id = text("id"),
    code = text("code"),
    host = text("host"),
    mode = if (text("mode").equals("open", ignoreCase = true)) JamMode.OPEN else JamMode.DEMOCRACY,
    isHost = this["isHost"]?.jsonPrimitive?.booleanOrNull ?: false,
    members = this["members"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
    queue = this["queue"]?.jsonArray?.map { it.jsonObject.toJamTrack() }.orEmpty(),
    proposals = this["proposals"]?.jsonArray?.map { it.jsonObject.toJamTrack() }.orEmpty(),
    nowPlaying = (this["nowPlaying"] as? JsonObject)?.let { now ->
        JamNowPlaying(
            trackId = now.text("trackId"),
            title = now.text("title"),
            artist = now.text("artist"),
            artworkUrl = now["artworkUrl"]?.jsonPrimitive?.contentOrNull,
            durationMs = now["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            positionMs = now["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            skipVotes = now["skipVotes"]?.jsonPrimitive?.longOrNull ?: 0L,
            skipsNeeded = now["skipsNeeded"]?.jsonPrimitive?.longOrNull ?: 1L,
            youSkipped = now["youSkipped"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    },
    approvalsNeeded = this["approvalsNeeded"]?.jsonPrimitive?.longOrNull ?: 1L,
    openToFriends = text("visibility").equals("friends", ignoreCase = true)
)

private fun JsonObject.toJamTrack(): JamTrack = JamTrack(
    id = text("id"),
    addedBy = text("addedBy"),
    trackUri = text("trackUri"),
    title = text("title"),
    artist = text("artist"),
    artworkUrl = this["artworkUrl"]?.jsonPrimitive?.contentOrNull,
    durationMs = this["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
    approvals = this["approvals"]?.jsonPrimitive?.longOrNull ?: 0L,
    approved = this["approved"]?.jsonPrimitive?.booleanOrNull ?: false,
    stillNeeded = this["stillNeeded"]?.jsonPrimitive?.longOrNull ?: 0L
)
