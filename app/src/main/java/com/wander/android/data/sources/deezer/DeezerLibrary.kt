package com.wander.android.data.sources.deezer

import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The signed-in account's own Deezer library — favourite songs, playlists, saved albums — and
 * liking, through the same gateway session a stream uses. The public API only serves these to an
 * OAuth token, which an `arl` sign-in does not have.
 *
 * Like [DeezerLyrics], a call refused because the session expired is tried once more on a fresh
 * one; any other refusal is the answer.
 */
@Singleton
class DeezerLibrary @Inject constructor(
    private val gateway: DeezerGateway,
    private val accountManager: DeezerAccountManager
) {
    /** The account's favourite songs, newest first, [limit] from [offset]. */
    suspend fun likedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        withSession { session ->
            val favourites = gateway.request(
                session,
                "song.getFavoriteIds",
                buildJsonObject { put("nb", limit); put("start", offset) }
            ).getOrThrow()
            val ids = DeezerGwParsing.list(favourites["data"]) { (it["SNG_ID"] as? JsonPrimitive)?.contentOrNull }
            if (ids.isEmpty()) return@withSession emptyList()
            val songs = gateway.request(
                session,
                "song.getListData",
                buildJsonObject { putJsonArray("SNG_IDS") { ids.forEach { add(it) } } }
            ).getOrThrow()
            // In the favourites' order, newest first, whatever order the details came back in.
            val byId = DeezerGwParsing.list(songs["data"], DeezerGwParsing::track).associateBy { it.id }
            ids.mapNotNull { byId["$PREFIX$it"] }
        }

    suspend fun playlists(): Result<List<UnifiedPlaylist>> = withSession { session ->
        val userId = session.userId ?: throw IOException("Deezer did not say which account is signed in")
        DeezerGwParsing.list(profileTab(session, "playlists", PROFILE_LIMIT)["data"]) {
            DeezerGwParsing.playlist(it, userId)
        }
    }

    suspend fun playlistTracks(playlistId: String): Result<List<UnifiedTrack>> = withSession { session ->
        val songs = gateway.request(
            session,
            "playlist.getSongs",
            buildJsonObject { put("PLAYLIST_ID", playlistId.removePrefix(PREFIX)); put("nb", ALL) }
        ).getOrThrow()
        DeezerGwParsing.list(songs["data"], DeezerGwParsing::track)
    }

    /** The profile page has no offset, so the first [offset] + [limit] are read and cut. */
    suspend fun albums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> = withSession { session ->
        DeezerGwParsing.list(profileTab(session, "albums", offset + limit)["data"], DeezerGwParsing::album)
            .drop(offset)
            .take(limit)
    }

    suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> = withSession { session ->
        gateway.request(
            session,
            if (liked) "favorite_song.add" else "favorite_song.remove",
            buildJsonObject { put("SNG_ID", trackId.removePrefix(PREFIX)) }
        ).getOrThrow()
        Unit
    }

    private fun profileTab(session: DeezerGateway.Session, tab: String, count: Int): JsonObject {
        val userId = session.userId ?: throw IOException("Deezer did not say which account is signed in")
        val page = gateway.request(
            session,
            "deezer.pageProfile",
            buildJsonObject { put("USER_ID", userId); put("tab", tab); put("nb", count) }
        ).getOrThrow()
        return page["TAB"]?.jsonObject?.get(tab)?.jsonObject
            ?: throw IOException("Deezer's profile page had no $tab")
    }

    private suspend fun <T> withSession(block: (DeezerGateway.Session) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            val arl = accountManager.arl
            if (arl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Deezer's library requires signing in"))
            }
            val first = gateway.session(arl).mapCatching(block)
            if ((first.exceptionOrNull() as? DeezerGateway.GatewayException)?.isSessionExpired != true) {
                return@withContext first
            }
            gateway.invalidate()
            gateway.session(arl).mapCatching(block)
        }

    private companion object {
        const val PREFIX = "deezer:"
        const val PROFILE_LIMIT = 500

        /** "Every entry", in the gateway's paging. */
        const val ALL = -1
    }
}
