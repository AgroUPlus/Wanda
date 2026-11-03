package com.wander.android.data.sources.ytmusic

import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The playlist-writing half of InnerTube, as music.youtube.com's own editor calls it. Every call
 * needs the signed-in cookie; the caller checks for one first.
 *
 * Playlists are addressed here by their bare id (`PL…`), not by the `VL…` browse id the rest of
 * this source hands out — see [editablePlaylistId].
 */
internal suspend fun InnerTubeClient.createPlaylist(title: String, videoIds: List<String>): Result<String> =
    transport.post(
        "playlist/create",
        buildJsonObject {
            put("context", transport.webContext())
            put("title", title)
            put("privacyStatus", "PRIVATE")
            if (videoIds.isNotEmpty()) putJsonArray("videoIds") { videoIds.forEach { add(it) } }
        }
    ).mapCatching { body ->
        body["playlistId"].text()?.takeIf { it.isNotBlank() }
            ?: throw IOException("YouTube Music created no playlist")
    }

internal suspend fun InnerTubeClient.deletePlaylist(playlistId: String): Result<Unit> =
    transport.post(
        "playlist/delete",
        buildJsonObject {
            put("context", transport.webContext())
            put("playlistId", playlistId)
        }
    ).map { }

/** One `browse/edit_playlist` call carrying [actions], each built as the web editor builds it. */
internal suspend fun InnerTubeClient.editPlaylist(playlistId: String, actions: JsonArray): Result<Unit> =
    transport.post(
        "browse/edit_playlist",
        buildJsonObject {
            put("context", transport.webContext())
            put("playlistId", playlistId)
            put("actions", actions)
        }
    ).mapCatching { body ->
        if (!body.editSucceeded()) throw IOException("YouTube Music refused the playlist change")
    }

/**
 * Whether an edit took. A body that says nothing about it is taken as success, since the delete
 * endpoint and some edits answer with no status at all; one that names another status is not.
 */
internal fun JsonObject.editSucceeded(): Boolean {
    val status = this["status"].text() ?: return true
    return status == EDIT_SUCCEEDED
}

private const val EDIT_SUCCEEDED = "STATUS_SUCCEEDED"

/**
 * The bare id to edit, from one of this source's playlist ids, or null if it is not a playlist the
 * account can own. Only `PL…` playlists are user playlists: Liked Music (`LM`), Episodes for later
 * (`SE`), radios and album playlists are YouTube's, and edits to them are refused up front rather
 * than sent to be refused by the server.
 */
internal fun editablePlaylistId(playlistId: String): String? =
    playlistId.removePrefix(YTM_PREFIX).removePrefix("VL").takeIf { it.startsWith("PL") }

/** The id this source names a playlist by, from the bare id the editor returns. */
internal fun browsablePlaylistId(bareId: String): String = "${YTM_PREFIX}VL$bareId"

/** One entry of a playlist as the editor addresses it: the song, and which copy of it. */
internal data class PlaylistEntryRef(val videoId: String, val setVideoId: String?)

/**
 * Each entry's ids on one page of a playlist, in the same order and under the same filter as the
 * tracks parsed off it — so index *i* here is the track at index *i* there. The `setVideoId` is
 * what identifies one *entry*: the same song can be in a playlist twice.
 */
internal fun JsonObject.playlistEntryRefs(): List<PlaylistEntryRef> =
    responsiveListItems().mapNotNull { row ->
        val track = parseResponsiveListItem(row) ?: return@mapNotNull null
        PlaylistEntryRef(
            videoId = track.id.removePrefix(YTM_PREFIX),
            setVideoId = row.path("playlistItemData", "playlistSetVideoId").text()
        )
    }

/**
 * The entry that should follow the moved one once the move is done, which is how
 * `ACTION_MOVE_VIDEO_BEFORE` expresses a position; null means it moves to the end.
 * [from] and [to] are as in `IMusicSource.moveInPlaylist`: afterwards the entry sits at [to].
 */
internal fun <T> successorAfterMove(entries: List<T>, from: Int, to: Int): T? {
    val rest = entries.toMutableList()
    rest.removeAt(from)
    return rest.getOrNull(to)
}
