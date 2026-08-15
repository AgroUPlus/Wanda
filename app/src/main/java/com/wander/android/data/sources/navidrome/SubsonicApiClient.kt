package com.wander.android.data.sources.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subsonic REST client. Authentication is salt+token per request (never the plaintext password
 * on the wire), matching the scheme Navidrome expects.
 */
@Singleton
class SubsonicApiClient @Inject constructor(
    private val client: HttpClient
) {
    private var baseUrl: String = ""
    private var username: String = ""
    private var password: String = ""

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun configure(serverUrl: String, user: String, pass: String) {
        baseUrl = serverUrl.trim().trimEnd('/')
        username = user.trim()
        password = pass.trim()
    }

    private fun authParams(): Map<String, String> {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }.toHex()
        return mapOf(
            "u" to username,
            "t" to md5(password + salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT_NAME,
            "f" to "json"
        )
    }

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

    suspend fun getAlbumList2(type: String, size: Int): Result<List<SubsonicAlbum>> =
        call("getAlbumList2.view", "type" to type, "size" to size)
            .map { it.albumList2?.album.orEmpty() }

    suspend fun getStarred2(): Result<SubsonicStarred2> =
        call("getStarred2.view").map { it.starred2 ?: SubsonicStarred2() }

    suspend fun getPlaylists(): Result<List<SubsonicPlaylist>> =
        call("getPlaylists.view").map { it.playlists?.playlist.orEmpty() }

    suspend fun getPlaylist(playlistId: String): Result<SubsonicPlaylistDetail> =
        call("getPlaylist.view", "id" to playlistId).mapCatching {
            it.playlist ?: throw IOException("Playlist $playlistId not found")
        }

    suspend fun getLyricsBySongId(id: String): Result<SubsonicLyricsList?> =
        call("getLyricsBySongId.view", "id" to id).map { it.lyricsList }

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
    fun buildStreamUrl(trackId: String): String =
        buildUrl("stream.view", mapOf("id" to trackId))

    /**
     * 500 px was fine for a list row and soft as a full-screen player cover on a 1080 px-wide
     * phone. Coil still decodes down to whatever each surface asks for, so the larger fetch costs
     * bytes once and is cached — it does not cost memory per view.
     */
    fun buildCoverArtUrl(coverArtId: String?, size: Int = 1000): String? {
        if (coverArtId.isNullOrBlank()) return null
        return buildUrl("getCoverArt.view", mapOf("id" to coverArtId, "size" to size.toString()))
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>): String =
        URLBuilder("$baseUrl/rest/$endpoint").apply {
            (authParams() + params).forEach { (k, v) -> parameters.append(k, v) }
        }.buildString()

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "Wanda"
        const val SALT_BYTES = 8

        fun md5(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).toHex()

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
