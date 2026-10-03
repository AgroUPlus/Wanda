package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException

/** Who besides its owner may edit a playlist on Agro. See `playlist_access` in Agro. */
enum class EditAccess {
    OFF, FRIENDS, PUBLIC;

    /** The widest access [visibility] allows, as the server narrows it: nobody edits what they cannot open. */
    fun clampedTo(visibility: PlaylistVisibility): EditAccess = when {
        visibility == PlaylistVisibility.PRIVATE -> OFF
        this == PUBLIC && visibility == PlaylistVisibility.FRIENDS -> FRIENDS
        else -> this
    }

    /** Whether [visibility] allows this level at all. */
    fun allowedBy(visibility: PlaylistVisibility): Boolean = clampedTo(visibility) == this
}

/** What this account may do with a playlist on Agro. */
enum class PlaylistRole {
    OWNER, EDITOR, CONTRIBUTOR, VIEWER, NONE;

    val canAdd: Boolean get() = this == OWNER || this == EDITOR || this == CONTRIBUTOR

    /** Removing or moving any track, not only one's own. */
    val canRearrange: Boolean get() = this == OWNER || this == EDITOR
}

data class AgroSharedItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val addedBy: String?,
    val addedAt: String?
)

/** A playlist on Agro as this account sees it, with the revision an edit must name. */
data class AgroSharedPlaylist(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String?,
    val visibility: PlaylistVisibility,
    val editAccess: EditAccess,
    val myRole: PlaylistRole,
    val revision: Long,
    val isFollowing: Boolean,
    val items: List<AgroSharedItem>
)

/** Where a playlist stands, without its tracks. [revision] is null when [accessible] is false. */
data class AgroPlaylistRevision(val id: String, val revision: Long?, val accessible: Boolean)

/** One change to send. Items are addressed by id, never by position. */
sealed interface AgroPlaylistEdit {
    /** Appended at the end. */
    data class Add(val track: AgroPlaylistTrack) : AgroPlaylistEdit
    data class Remove(val itemId: String) : AgroPlaylistEdit
    /** Placed after [afterItemId], or first when it is null. */
    data class Move(val itemId: String, val afterItemId: String?) : AgroPlaylistEdit
}

internal object AgroSharedPlaylistParsing {
    const val ITEM_FIELDS = "id title artist album durationMs addedBy addedAt"
    const val PLAYLIST_FIELDS =
        "id userId title description visibility editAccess myRole revision isFollowing items { $ITEM_FIELDS }"

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /**
     * Anything this version does not recognise reads closed — no editing, not shared — so a value
     * from a newer server can never give this device more than it was granted.
     */
    private inline fun <reified E : Enum<E>> JsonObject.enum(key: String, closed: E): E =
        enumValues<E>().firstOrNull { it.name == string(key) } ?: closed

    fun playlist(json: JsonObject?): AgroSharedPlaylist {
        json ?: throw IOException("Agro has no such playlist")
        return AgroSharedPlaylist(
            id = json.string("id") ?: throw IOException("Agro returned a playlist without an id"),
            ownerId = json.string("userId").orEmpty(),
            title = json.string("title").orEmpty(),
            description = json.string("description"),
            visibility = json.enum("visibility", PlaylistVisibility.PRIVATE),
            editAccess = json.enum("editAccess", EditAccess.OFF),
            myRole = json.enum("myRole", PlaylistRole.VIEWER),
            revision = json["revision"]?.jsonPrimitive?.longOrNull
                ?: throw IOException("Agro returned a playlist without a revision"),
            isFollowing = json["isFollowing"]?.jsonPrimitive?.booleanOrNull == true,
            items = (json["items"] as? JsonArray).orEmpty().map { item(it.jsonObject) }
        )
    }

    private fun item(json: JsonObject) = AgroSharedItem(
        id = json.string("id") ?: throw IOException("Agro returned a track without an id"),
        title = json.string("title").orEmpty(),
        artist = json.string("artist").orEmpty(),
        album = json.string("album"),
        durationMs = json["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        addedBy = json.string("addedBy"),
        addedAt = json.string("addedAt")
    )

    fun revisions(json: JsonArray?): List<AgroPlaylistRevision> = json.orEmpty().map {
        val entry = it.jsonObject
        AgroPlaylistRevision(
            id = entry.string("id").orEmpty(),
            revision = entry["revision"]?.jsonPrimitive?.longOrNull,
            accessible = entry["accessible"]?.jsonPrimitive?.booleanOrNull == true
        )
    }
}
