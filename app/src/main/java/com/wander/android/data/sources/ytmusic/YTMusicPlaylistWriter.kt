package com.wander.android.data.sources.ytmusic

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Creating, editing and deleting the signed-in account's own YouTube Music playlists.
 *
 * Entries are addressed by index from the outside and by `setVideoId` on the wire, so removing or
 * moving one first re-reads the playlist's set ids, every page of them, in the order the tracks
 * were listed — an index is only meaningful against the playlist as it stands now.
 */
@Singleton
class YTMusicPlaylistWriter @Inject constructor(
    private val innerTube: InnerTubeClient
) {
    suspend fun create(name: String, trackIds: List<String>): Result<String> {
        val videoIds = trackIds.map { id ->
            id.removePrefix(YTM_PREFIX).takeIf { id.startsWith(YTM_PREFIX) && it.isNotBlank() }
                ?: return Result.failure(IllegalArgumentException("Not a YouTube Music track: $id"))
        }
        return innerTube.createPlaylist(name, videoIds).map(::browsablePlaylistId)
    }

    suspend fun add(playlistId: String, trackIds: List<String>): Result<Unit> = edit(playlistId) {
        buildJsonArray {
            trackIds.forEach { id ->
                require(id.startsWith(YTM_PREFIX)) { "Not a YouTube Music track: $id" }
                addJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", id.removePrefix(YTM_PREFIX))
                }
            }
        }
    }

    suspend fun remove(playlistId: String, index: Int): Result<Unit> = edit(playlistId) {
        val entry = entryRefs(playlistId).getOrThrow().getOrNull(index) ?: throw noEntry(index)
        val setVideoId = entry.setVideoId ?: throw noEntry(index)
        buildJsonArray {
            addJsonObject {
                put("action", "ACTION_REMOVE_VIDEO")
                put("setVideoId", setVideoId)
                put("removedVideoId", entry.videoId)
            }
        }
    }

    suspend fun move(playlistId: String, from: Int, to: Int): Result<Unit> = edit(playlistId) {
        val entries = entryRefs(playlistId).getOrThrow()
        if (from !in entries.indices || to !in entries.indices) throw noEntry(from)
        val moved = entries[from].setVideoId ?: throw noEntry(from)
        val successor = successorAfterMove(entries, from, to)?.let { it.setVideoId ?: throw noEntry(to) }
        buildJsonArray {
            addJsonObject {
                put("action", "ACTION_MOVE_VIDEO_BEFORE")
                put("setVideoId", moved)
                successor?.let { put("movedSetVideoIdSuccessor", it) }
            }
        }
    }

    suspend fun updateDetails(playlistId: String, name: String, comment: String?): Result<Unit> =
        edit(playlistId) {
            buildJsonArray {
                addJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_NAME")
                    put("playlistName", name)
                }
                addJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    put("playlistDescription", comment?.takeIf { it.isNotBlank() }.orEmpty())
                }
            }
        }

    suspend fun delete(playlistId: String): Result<Unit> {
        val bareId = editablePlaylistId(playlistId) ?: return notEditable()
        return innerTube.deletePlaylist(bareId)
    }

    /** Builds the actions against the bare id and sends them; any failure building them is the result. */
    private suspend fun edit(playlistId: String, actions: suspend (String) -> JsonArray): Result<Unit> {
        val bareId = editablePlaylistId(playlistId) ?: return notEditable()
        return runCatching { actions(bareId) }.fold(
            onSuccess = { innerTube.editPlaylist(bareId, it) },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun entryRefs(playlistId: String): Result<List<PlaylistEntryRef>> =
        InnerTubeShelfCursor(
            firstPage = { innerTube.browse(playlistId.removePrefix(YTM_PREFIX)) },
            nextPage = { innerTube.browseContinuation(it) },
            parse = { it.playlistEntryRefs() }
        ).all()

    private fun notEditable(): Result<Nothing> =
        Result.failure(UnsupportedOperationException("YouTube Music does not let this playlist be edited"))

    private fun noEntry(index: Int) =
        IOException("YouTube Music's copy of this playlist has no entry $index; refresh it and try again")
}
