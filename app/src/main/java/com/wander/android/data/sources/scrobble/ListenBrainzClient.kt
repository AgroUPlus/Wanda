package com.wander.android.data.sources.scrobble

import com.wander.android.BuildConfig
import com.wander.android.core.database.dao.PendingScrobble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ListenBrainz's API (listenbrainz.readthedocs.io, "core"), over the shared HTTPS client.
 *
 * A user token — from listenbrainz.org/settings — in the `Authorization` header is the whole of
 * signing in, and the only secret; it never goes in a URL.
 */
@Singleton
internal class ListenBrainzClient @Inject constructor(private val http: OkHttpClient) {

    /** The account [token] belongs to, or a failure saying it does not belong to one. */
    suspend fun validate(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$API/1/validate-token").header("Authorization", "Token $token").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("ListenBrainz answered HTTP ${response.code}")
                val json = JSON.parseToJsonElement(response.body.string()) as? JsonObject
                if (json?.get("valid")?.jsonPrimitive?.booleanOrNull != true) throw IOException("ListenBrainz does not know that token")
                json["user_name"]?.jsonPrimitive?.contentOrNull ?: throw IOException("ListenBrainz sent no user name")
            }
        }
    }

    /** Sends up to [BATCH] plays as one submission. */
    suspend fun submit(token: String, plays: List<PendingScrobble>): ScrobbleOutcome {
        val batch = plays.take(BATCH)
        val body = buildJsonObject {
            put("listen_type", if (batch.size == 1) "single" else "import")
            put("payload", buildJsonArray {
                batch.forEach { play ->
                    add(buildJsonObject {
                        put("listened_at", play.startedAtSeconds())
                        put("track_metadata", metadata(play.title, play.artist, play.album, play.durationMs))
                    })
                }
            })
        }
        return send(token, body)
    }

    suspend fun playingNow(token: String, track: NowPlaying): ScrobbleOutcome = send(
        token,
        buildJsonObject {
            put("listen_type", "playing_now")
            put("payload", buildJsonArray {
                add(buildJsonObject { put("track_metadata", metadata(track.title, track.artist, track.album, track.durationMs)) })
            })
        }
    )

    private fun metadata(title: String, artist: String, album: String?, durationMs: Long) = buildJsonObject {
        put("artist_name", artist)
        put("track_name", title)
        album?.takeIf { it.isNotBlank() }?.let { put("release_name", it) }
        put("additional_info", buildJsonObject {
            if (durationMs > 0) put("duration_ms", durationMs)
            submittedBy()
        })
    }

    private fun JsonObjectBuilder.submittedBy() {
        put("media_player", SUBMISSION_CLIENT)
        put("submission_client", SUBMISSION_CLIENT)
        put("submission_client_version", BuildConfig.VERSION_NAME)
    }

    private suspend fun send(token: String, body: JsonObject): ScrobbleOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API/1/submit-listens")
            .header("Authorization", "Token $token")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> ScrobbleOutcome.Done
                    response.code == 401 -> ScrobbleOutcome.Unauthorized("ListenBrainz no longer accepts this token")
                    // A listen it will never take — malformed, say — is not worth sending again.
                    response.code == 400 -> ScrobbleOutcome.Done
                    else -> ScrobbleOutcome.Retry("ListenBrainz answered HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            ScrobbleOutcome.Retry(e.message ?: "ListenBrainz could not be reached")
        }
    }

    companion object {
        /** Far under the server's 1000-listen limit, so one request stays small on a poor connection. */
        const val BATCH = 100
        private const val API = "https://api.listenbrainz.org"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
