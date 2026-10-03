package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Who on the server can open a published playlist. The owner always can. */
enum class PlaylistVisibility { PRIVATE, FRIENDS, PUBLIC }

/**
 * Playlists on the paired Agro server, used to share one with the other accounts on it.
 *
 * Publishing sets who can fetch the playlist by id: only the owner, the owner's friends, or every
 * signed-in account; the server refuses anyone who is not signed in. Tracks are added in
 * aliased batches: mutation fields run in order, so positions come out right, and one request per
 * batch keeps a long playlist from costing a round trip per track.
 */
@Singleton
class AgroPlaylistApi @Inject constructor(private val graphQl: AgroGraphQl) {

    val isAvailable: Boolean get() = graphQl.isConfigured

    /** Creates a playlist holding [tracks] and returns its id, or removes what it made and fails. */
    suspend fun publish(
        name: String,
        tracks: List<AgroPlaylistTrack>,
        visibility: PlaylistVisibility
    ): Result<String> {
        val created = graphQl.execute(createQuery(visibility), createVariables(name, visibility))
            .recoverCatching { error -> throw friendlyVisibilityError(error, visibility) }
            .mapCatching { data ->
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

    /**
     * Deletes the published playlist [id] from the server. The server answers false when there is
     * no such playlist of this account's, which for a copy this account published means it is
     * already gone: either way nothing is left there, so both count as done.
     */
    suspend fun delete(id: String): Result<Unit> =
        graphQl.execute(DELETE, buildJsonObject { put("id", id) }).mapCatching { data ->
            data["deletePlaylist"]?.jsonPrimitive?.booleanOrNull
                ?: throw IOException("Agro did not answer the delete")
        }

    /** Changes who can open the published playlist [id]. Only its owner may. */
    suspend fun updateVisibility(id: String, visibility: PlaylistVisibility): Result<Unit> =
        graphQl.execute(updateVisibilityQuery(visibility), updateVisibilityVariables(id, visibility))
            .recoverCatching { error -> throw friendlyVisibilityError(error, visibility) }
            .mapCatching { data ->
                if (data["updatePlaylistVisibility"]?.jsonPrimitive?.booleanOrNull != true) {
                    throw IOException("Agro did not change the playlist's visibility")
                }
            }

    internal companion object {
        /**
         * Each aliased mutation costs the server two units of query complexity, and it refuses a
         * request over 500. Fifty keeps a batch at a fifth of that.
         */
        const val BATCH = 50
        private const val MAX_TITLE = 255
        private const val MAX_FIELD = 500

        /**
         * Public and private use the `isPublic` flag every server has had since playlists existed,
         * so they keep working against one that has not been updated. Only friends-only needs the
         * newer `visibility` argument.
         */
        fun createQuery(visibility: PlaylistVisibility): String = when (visibility) {
            PlaylistVisibility.FRIENDS ->
                "mutation(\$title: String!, \$visibility: PlaylistVisibility) { createPlaylist(title: \$title, visibility: \$visibility) { id } }"
            else ->
                "mutation(\$title: String!, \$isPublic: Boolean) { createPlaylist(title: \$title, isPublic: \$isPublic) { id } }"
        }

        fun createVariables(name: String, visibility: PlaylistVisibility): JsonObject = buildJsonObject {
            put("title", name.take(MAX_TITLE))
            if (visibility == PlaylistVisibility.FRIENDS) put("visibility", "FRIENDS")
            else put("isPublic", visibility == PlaylistVisibility.PUBLIC)
        }

        /** The same split as [createQuery], for the same reason. */
        fun updateVisibilityQuery(visibility: PlaylistVisibility): String = when (visibility) {
            PlaylistVisibility.FRIENDS ->
                "mutation(\$id: String!, \$visibility: PlaylistVisibility) { updatePlaylistVisibility(playlistId: \$id, visibility: \$visibility) }"
            else ->
                "mutation(\$id: String!, \$isPublic: Boolean) { updatePlaylistVisibility(playlistId: \$id, isPublic: \$isPublic) }"
        }

        fun updateVisibilityVariables(id: String, visibility: PlaylistVisibility): JsonObject = buildJsonObject {
            put("id", id)
            if (visibility == PlaylistVisibility.FRIENDS) put("visibility", "FRIENDS")
            else put("isPublic", visibility == PlaylistVisibility.PUBLIC)
        }

        /** An older server rejects the friends-only argument by name; say what to do about it. */
        fun friendlyVisibilityError(error: Throwable, visibility: PlaylistVisibility): Throwable =
            if (visibility == PlaylistVisibility.FRIENDS && error.message?.contains("PlaylistVisibility") == true) {
                IOException("This Agro server is too old for friends-only playlists. Update it, or choose another option.")
            } else {
                error
            }
        private const val DELETE = "mutation(\$id: String!) { deletePlaylist(playlistId: \$id) }"
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
