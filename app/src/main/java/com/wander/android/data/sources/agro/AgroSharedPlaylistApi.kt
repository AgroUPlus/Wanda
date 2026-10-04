package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeping a copy of a playlist on the paired Agro server in step with it: reading it, following
 * it, sending edits against a revision, and cheaply asking which copies have changed.
 *
 * An edit made against an old revision fails with [AgroStaleRevision] — see [AgroGraphQl].
 */
@Singleton
class AgroSharedPlaylistApi @Inject constructor(private val graphQl: AgroGraphQl) {

    val isAvailable: Boolean get() = graphQl.isConfigured

    private val fields: String
        get() = AgroSharedPlaylistParsing.playlistFields(graphQl.serverSupports(AgroSharedPlaylistParsing.BLENDS))

    /** The paired account's name, which is how an item says it was added by this account. */
    val me: String get() = graphQl.userId

    suspend fun fetch(id: String): Result<AgroSharedPlaylist> =
        graphQl.execute("query(\$id: String!) { playlist(id: \$id) { $fields } }", idVariables(id))
            .mapCatching { AgroSharedPlaylistParsing.playlist(it["playlist"] as? JsonObject) }

    suspend fun follow(id: String): Result<AgroSharedPlaylist> =
        graphQl.execute("mutation(\$id: String!) { followPlaylist(id: \$id) { $fields } }", idVariables(id))
            .mapCatching { AgroSharedPlaylistParsing.playlist(it["followPlaylist"] as? JsonObject) }

    suspend fun unfollow(id: String): Result<Unit> =
        graphQl.execute("mutation(\$id: String!) { unfollowPlaylist(id: \$id) }", idVariables(id)).map { }

    /** Applies [edits] all or none, provided the playlist is still at [baseRevision]. */
    suspend fun applyEdits(id: String, baseRevision: Long, edits: List<AgroPlaylistEdit>): Result<AgroSharedPlaylist> =
        graphQl.execute(
            "mutation(\$id: String!, \$base: Int!, \$edits: [PlaylistEditInput!]!) { " +
                "applyPlaylistEdits(playlistId: \$id, baseRevision: \$base, edits: \$edits) { $fields } }",
            buildJsonObject {
                put("id", id)
                put("base", baseRevision)
                put("edits", editsJson(edits))
            }
        ).mapCatching { AgroSharedPlaylistParsing.playlist(it["applyPlaylistEdits"] as? JsonObject) }

    /** Sets who besides the owner may edit, and answers what the server actually stored. */
    suspend fun updateEditAccess(id: String, access: EditAccess): Result<EditAccess> =
        graphQl.execute(
            "mutation(\$id: String!, \$a: EditAccess!) { updatePlaylistEditAccess(playlistId: \$id, editAccess: \$a) }",
            buildJsonObject {
                put("id", id)
                put("a", access.name)
            }
        ).mapCatching { data ->
            val stored = data["updatePlaylistEditAccess"]?.jsonPrimitive?.contentOrNull
            EditAccess.entries.firstOrNull { it.name == stored }
                ?: throw IOException("Agro did not say who can edit the playlist now")
        }

    /** Which of [ids] changed, without downloading any of them. */
    suspend fun revisions(ids: List<String>): Result<List<AgroPlaylistRevision>> =
        graphQl.execute(
            "query(\$ids: [String!]!) { playlistRevisions(ids: \$ids) { id revision accessible } }",
            buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }
        ).mapCatching { AgroSharedPlaylistParsing.revisions(it["playlistRevisions"] as? JsonArray) }

    /**
     * The ids of the playlists this account owns on the server, however they were made — from
     * Wanda, or from Agro's own dashboard, which Wanda would otherwise never hear about.
     */
    suspend fun ownedIds(): Result<List<String>> =
        graphQl.execute("query { playlists { id userId } }", buildJsonObject {}).mapCatching { data ->
            val listed = data["playlists"] as? JsonArray ?: throw IOException("Agro did not list its playlists")
            listed.mapNotNull { it as? JsonObject }
                .filter { it["userId"]?.jsonPrimitive?.contentOrNull.equals(me, ignoreCase = true) }
                .mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }
        }

    private fun idVariables(id: String) = buildJsonObject { put("id", id) }

    internal companion object {
        private const val MAX_FIELD = 500

        fun editsJson(edits: List<AgroPlaylistEdit>): JsonArray = buildJsonArray {
            edits.forEach { edit ->
                add(
                    when (edit) {
                        is AgroPlaylistEdit.Add -> buildJsonObject {
                            put("add", buildJsonObject { put("track", trackJson(edit.track)) })
                        }
                        is AgroPlaylistEdit.Remove -> buildJsonObject { put("remove", edit.itemId) }
                        is AgroPlaylistEdit.Move -> buildJsonObject {
                            put(
                                "move",
                                buildJsonObject {
                                    put("itemId", edit.itemId)
                                    edit.afterItemId?.let { put("afterItemId", it) }
                                }
                            )
                        }
                    }
                )
            }
        }

        private fun trackJson(track: AgroPlaylistTrack) = buildJsonObject {
            put("title", track.title.take(MAX_FIELD))
            put("artist", track.artist.take(MAX_FIELD))
            track.album?.takeIf { it.isNotBlank() }?.let { put("album", it.take(MAX_FIELD)) }
            if (track.durationMs > 0) put("durationMs", track.durationMs)
        }
    }
}
