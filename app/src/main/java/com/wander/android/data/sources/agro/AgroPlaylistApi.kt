package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One track of a playlist kept on an Agro server: what it is, never where it plays from. */
data class AgroPlaylistTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)

data class AgroPlaylist(val title: String, val tracks: List<AgroPlaylistTrack>)

/**
 * Playlists on the paired Agro server, used to share one with the other accounts on it.
 *
 * Publishing makes the playlist public on the server, which is what lets another signed-in
 * account fetch it by id; the server refuses anyone who is not signed in. Tracks are added in
 * aliased batches: mutation fields run in order, so positions come out right, and one request per
 * batch keeps a long playlist from costing a round trip per track.
 */
@Singleton
class AgroPlaylistApi @Inject constructor(private val graphQl: AgroGraphQl) {

    val isAvailable: Boolean get() = graphQl.isConfigured

    /** Creates a public playlist holding [tracks] and returns its id, or removes what it made and fails. */
    suspend fun publish(name: String, tracks: List<AgroPlaylistTrack>): Result<String> {
        val created = graphQl.execute(
            CREATE,
            buildJsonObject {
                put("title", name.take(MAX_TITLE))
                put("isPublic", true)
            }
        ).mapCatching { data ->
            data["createPlaylist"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                ?: throw IOException("Agro did not return the new playlist")
        }
        val id = created.getOrElse { return Result.failure(it) }

        for (batch in tracks.chunked(BATCH)) {
            graphQl.execute(addTracksQuery(batch.size), addTracksVariables(id, batch)).onFailure { error ->
                // A half-filled public playlist is worse than none.
                graphQl.execute(DELETE, buildJsonObject { put("id", id) })
                return Result.failure(error)
            }
        }
        return Result.success(id)
    }

    suspend fun fetch(id: String): Result<AgroPlaylist> =
        graphQl.execute(FETCH, buildJsonObject { put("id", id) }).mapCatching { data ->
            val playlist = data["playlist"]?.jsonObject ?: throw IOException("Agro has no such playlist")
            AgroPlaylist(
                title = playlist["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                tracks = (playlist["items"] as? JsonArray).orEmpty().map { it.jsonObject.toTrack() }
            )
        }

    private fun JsonObject.toTrack() = AgroPlaylistTrack(
        title = this["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        artist = this["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        album = this["album"]?.jsonPrimitive?.contentOrNull,
        durationMs = this["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L
    )

    internal companion object {
        /**
         * Each aliased mutation costs the server two units of query complexity, and it refuses a
         * request over 500. Fifty keeps a batch at a fifth of that.
         */
        const val BATCH = 50
        private const val MAX_TITLE = 255
        private const val MAX_FIELD = 500

        private const val CREATE =
            "mutation(\$title: String!, \$isPublic: Boolean) { createPlaylist(title: \$title, isPublic: \$isPublic) { id } }"
        private const val DELETE = "mutation(\$id: String!) { deletePlaylist(playlistId: \$id) }"
        private const val FETCH =
            "query(\$id: String!) { playlist(id: \$id) { title items { title artist album durationMs } } }"

        fun addTracksQuery(count: Int): String = buildString {
            append("mutation(\$id: String!")
            repeat(count) { append(", \$t").append(it).append(": PlaylistTrackInput!") }
            append(") {")
            repeat(count) { append(" a").append(it).append(": addTrackToPlaylist(playlistId: \$id, track: \$t").append(it).append(") { id }") }
            append(" }")
        }

        fun addTracksVariables(playlistId: String, tracks: List<AgroPlaylistTrack>): JsonObject = buildJsonObject {
            put("id", playlistId)
            tracks.forEachIndexed { index, track ->
                put(
                    "t$index",
                    buildJsonObject {
                        put("title", track.title.take(MAX_FIELD))
                        put("artist", track.artist.take(MAX_FIELD))
                        track.album?.takeIf { it.isNotBlank() }?.let { put("album", it.take(MAX_FIELD)) }
                        if (track.durationMs > 0) put("durationMs", JsonPrimitive(track.durationMs))
                    }
                )
            }
        }
    }
}
