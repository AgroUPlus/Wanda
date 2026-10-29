package com.wander.android.data.sources.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subsonic REST client. Authentication is salt+token per request (never the plaintext password
 * on the wire), matching the scheme Navidrome expects.
 */
@Singleton
class SubsonicApiClient @Inject constructor(
    private val client: HttpClient,
    private val credentials: SubsonicCredentials
) {
    private val baseUrl: String get() = credentials.baseUrl

    val isConfigured: Boolean get() = credentials.isConfigured

    fun configure(serverUrl: String, user: String, pass: String) =
        credentials.configure(serverUrl, user, pass)

    private fun authParams(): Map<String, String> = credentials.authParams()

    /** Every endpoint funnels through here, so auth and error mapping exist in exactly one place. */
    private suspend fun call(
        endpoint: String,
        vararg params: Pair<String, Any?>
    ): Result<SubsonicResponse> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Navidrome is not configured"))
        }
        runCatching {
            val root: SubsonicResponseRoot = client.get("$baseUrl/rest/$endpoint") {
                authParams().forEach { (k, v) -> parameter(k, v) }
                params.forEach { (k, v) -> if (v != null) parameter(k, v) }
            }.body()
            val body = root.response
            if (body.status != "ok") {
                throw IOException(body.error?.message ?: "Server rejected the request")
            }
            body
        }
    }

    suspend fun ping(): Result<Unit> = call("ping.view").map { }

    suspend fun getSong(songId: String): Result<SubsonicSong?> =
        call("getSong.view", "id" to songId).map { it.song }

    suspend fun search3(query: String): Result<SubsonicSearchResult3> =
        call(
            "search3.view",
            "query" to query,
            "songCount" to 50,
            "albumCount" to 20,
            "artistCount" to 10
        ).map { it.searchResult3 ?: SubsonicSearchResult3() }

    suspend fun getAlbum(albumId: String): Result<SubsonicAlbumDetail> =
        call("getAlbum.view", "id" to albumId).mapCatching {
            it.album ?: throw IOException("Album $albumId not found")
        }

    /**
     * One page of the album list.
     *
     * [offset] is not optional decoration. Subsonic caps `size` at 500 and returns nothing beyond
     * it, so a caller that never advances the offset sees the first page of the server and nothing
     * else — which is what limited a 203-album library to the 50 that one default-sized call
     * happened to return.
     */
    suspend fun getAlbumList2(type: String, size: Int, offset: Int = 0): Result<List<SubsonicAlbum>> =
        call("getAlbumList2.view", "type" to type, "size" to size, "offset" to offset)
            .map { it.albumList2?.album.orEmpty() }

    suspend fun getStarred2(): Result<SubsonicStarred2> =
        call("getStarred2.view").map { it.starred2 ?: SubsonicStarred2() }

    suspend fun getPlaylists(): Result<List<SubsonicPlaylist>> =
        call("getPlaylists.view").map { it.playlists?.playlist.orEmpty() }

    suspend fun getPlaylist(playlistId: String): Result<SubsonicPlaylistDetail> =
        call("getPlaylist.view", "id" to playlistId).mapCatching {
            it.playlist ?: throw IOException("Playlist $playlistId not found")
        }

    /**
     * Creates a playlist and returns its id.
     *
     * Subsonic overloads this endpoint: passing `playlistId` *replaces* an existing playlist
     * rather than making a new one, so only `name` is ever sent here. Older servers answer with an
     * empty body instead of the new playlist, which is why the id is looked up by name as a
     * fallback rather than assumed — see [NavidromeSource].
     */
    suspend fun createPlaylist(name: String, songIds: List<String>): Result<String?> =
        call(
            "createPlaylist.view",
            "name" to name,
            *songIds.map { "songId" to it as Any? }.toTypedArray()
        ).map { it.playlist?.id }

    /**
     * Appends tracks to an existing playlist.
     *
     * `updatePlaylist` adds rather than replaces, so this is safe to call repeatedly; the server
     * keeps duplicates if the same track is added twice, which matches what the web UI does.
     */
    suspend fun updatePlaylist(playlistId: String, songIdsToAdd: List<String>): Result<Unit> =
        call(
            "updatePlaylist.view",
            "playlistId" to playlistId,
            *songIdsToAdd.map { "songIdToAdd" to it as Any? }.toTypedArray()
        ).map { }

    suspend fun getLyricsBySongId(id: String): Result<SubsonicLyricsList?> =
        call("getLyricsBySongId.view", "id" to id).map { it.lyricsList }

    suspend fun getArtist(artistId: String): Result<SubsonicArtistDetail> =
        call("getArtist.view", "id" to artistId).mapCatching {
            it.artist ?: throw IOException("Artist $artistId not found")
        }

    /** Biography and portraits. Absent on servers with no metadata agent, which is not an error. */
    suspend fun getArtistInfo2(artistId: String): Result<SubsonicArtistInfo?> =
        call("getArtistInfo2.view", "id" to artistId).map { it.artistInfo2 }

    suspend fun getSimilarSongs2(id: String, count: Int): Result<List<SubsonicSong>> =
        call("getSimilarSongs2.view", "id" to id, "count" to count)
            .map { it.similarSongs2?.song.orEmpty() }

    /**
     * A public link to [ids], as the server itself publishes it — the same `createShare` call
     * Wander's share overlay makes, so a link shared from either client looks identical.
     *
     * Fails when sharing is disabled server-side, which is reported rather than worked around:
     * there is no way to mint a public URL without the server's cooperation.
     */
    suspend fun createShare(ids: List<String>, description: String): Result<String> =
        call(
            "createShare.view",
            *ids.map { "id" to it as Any? }.toTypedArray(),
            "description" to description.takeIf { it.isNotBlank() },
            "downloadable" to false
        ).mapCatching {
            it.shares?.share?.firstOrNull()?.url
                ?: throw IOException("The server accepted the share but returned no link")
        }

    /**
     * Lists active shares on this Navidrome / Subsonic server.
     */
    suspend fun getShares(): Result<List<SubsonicShare>> =
        call("getShares.view").mapCatching {
            it.shares?.share.orEmpty()
        }

    /**
     * Asks the server to rescan its music folders.
     *
     * Used after Agro files new uploads into the library: Navidrome would find them on its own
     * eventually, but "eventually" can be an hour, and the user just watched the upload finish.
     */
    suspend fun startScan(): Result<Unit> = call("startScan.view").map { }

    suspend fun star(id: String): Result<Unit> = call("star.view", "id" to id).map { }

    suspend fun unstar(id: String): Result<Unit> = call("unstar.view", "id" to id).map { }

    suspend fun scrobble(id: String, timeSeconds: Long): Result<Unit> =
        call("scrobble.view", "id" to id, "time" to timeSeconds, "submission" to true).map { }

    /**
     * Stream and cover URLs carry credentials in the query string, so they are built on demand
     * and never persisted or logged.
     */
    /**
     * `format=raw` is what makes the track seekable.
     *
     * Left to itself the server decides per client whether to transcode, and a transcode is
     * produced as it is sent — so there is no byte at an arbitrary offset to answer a `Range`
     * request with, and ExoPlayer's seek is refused or served from zero. The thumb springs back to
     * where it was. It only happens to the formats a given server is configured to convert, which
     * is why FLAC would not scrub while MP3 did.
     *
     * `estimateContentLength` stays for the case where a server transcodes anyway despite `raw`:
     * without it such a response carries no `Content-Length`, Media3 reports `TIME_UNSET` and the
     * duration falls back to the metadata's — see the resolution in `PlayerConnection.snapshot`.
     */
    fun buildStreamUrl(trackId: String): String =
        buildUrl("stream.view", mapOf(
            "id" to trackId,
            "format" to "raw",
            "estimateContentLength" to "true"
        ))

    /**
     * 500 px was fine for a list row and soft as a full-screen player cover on a 1080 px-wide
     * phone. Coil still decodes down to whatever each surface asks for, so the larger fetch costs
     * bytes once and is cached — it does not cost memory per view.
     */
    fun buildCoverArtUrl(coverArtId: String?, size: Int = 1000): String? {
        if (coverArtId.isNullOrBlank()) return null
        // No credentials: this URL is stored, shared and put on the media session. The clients
        // that load artwork sign it when they fetch it, see [SubsonicCredentials.sign].
        return URLBuilder("$baseUrl/rest/getCoverArt.view").apply {
            parameters.append("id", coverArtId)
            parameters.append("size", size.toString())
        }.buildString()
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>): String =
        URLBuilder("$baseUrl/rest/$endpoint").apply {
            (authParams() + params).forEach { (k, v) -> parameters.append(k, v) }
        }.buildString()
}
