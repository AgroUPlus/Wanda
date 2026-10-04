package com.wander.android.data.sources.scrobble

import com.wander.android.BuildConfig
import com.wander.android.core.database.dao.PendingScrobble
import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last.fm's scrobbling API, spoken directly over the shared HTTPS client.
 *
 * Every call is a signed POST. Nothing secret ever goes in a URL — the session key and signature
 * travel in the form body — so no request line that reaches a log can carry them.
 *
 * Signing in is Last.fm's desktop flow: a token, the user approving it in the browser, then the
 * token exchanged for a session that does not expire. The password never passes through Wanda.
 */
@Singleton
internal class LastFmClient @Inject constructor(
    private val http: OkHttpClient,
    private val secureStorage: SecureStorage
) {
    /** This build's key and secret, or the ones the user registered; null when there are neither. */
    private val app: Pair<String, String>?
        get() {
            val prefs = secureStorage.scrobbling
            val key = BuildConfig.LASTFM_API_KEY.ifBlank { prefs.lastFmApiKey.orEmpty() }
            val secret = BuildConfig.LASTFM_API_SECRET.ifBlank { prefs.lastFmApiSecret.orEmpty() }
            return (key to secret).takeIf { key.isNotBlank() && secret.isNotBlank() }
        }

    /** Whether this build can talk to Last.fm without the user pasting a key of their own. */
    val shipsAppKey: Boolean get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_API_SECRET.isNotBlank()
    val hasAppKey: Boolean get() = app != null

    /** Step one: a token to approve, and the page that approves it. */
    suspend fun beginSignIn(): Result<Pair<String, String>> {
        val key = app?.first ?: return Result.failure(IOException("No Last.fm API key is set"))
        return call(mapOf("method" to "auth.getToken")).mapCatching { json ->
            val token = json["token"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Last.fm sent no token")
            token to "https://www.last.fm/api/auth/?api_key=$key&token=$token"
        }
    }

    /** Step two, once approved: the account name and its session key. */
    suspend fun finishSignIn(token: String): Result<Pair<String, String>> =
        call(mapOf("method" to "auth.getSession", "token" to token)).mapCatching { json ->
            val session = json["session"]?.jsonObject ?: throw IOException("Last.fm has not been approved yet")
            val name = session["name"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Last.fm sent no account name")
            val key = session["key"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Last.fm sent no session")
            name to key
        }

    /** Sends up to [BATCH] plays. Ones Last.fm ignores — too short, too old — are done with too. */
    suspend fun scrobble(session: String, plays: List<PendingScrobble>): ScrobbleOutcome {
        val params = mutableMapOf("method" to "track.scrobble", "sk" to session)
        plays.take(BATCH).forEachIndexed { i, play ->
            params["artist[$i]"] = play.artist
            params["track[$i]"] = play.title
            params["timestamp[$i]"] = play.startedAtSeconds().toString()
            play.album?.takeIf { it.isNotBlank() }?.let { params["album[$i]"] = it }
            if (play.durationMs > 0) params["duration[$i]"] = (play.durationMs / 1000).toString()
        }
        return outcome(call(params))
    }

    suspend fun nowPlaying(session: String, track: NowPlaying): ScrobbleOutcome {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "sk" to session,
            "artist" to track.artist,
            "track" to track.title
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        if (track.durationMs > 0) params["duration"] = (track.durationMs / 1000).toString()
        return outcome(call(params))
    }

    private fun outcome(result: Result<JsonObject>): ScrobbleOutcome = result.fold(
        onSuccess = { ScrobbleOutcome.Done },
        onFailure = { error ->
            when ((error as? LastFmError)?.code) {
                // Invalid or revoked session, or a key Last.fm no longer accepts.
                4, 9, 10, 14, 26 -> ScrobbleOutcome.Unauthorized(error.message.orEmpty())
                else -> ScrobbleOutcome.Retry(error.message.orEmpty())
            }
        }
    )

    private suspend fun call(params: Map<String, String>): Result<JsonObject> = withContext(Dispatchers.IO) {
        val (key, secret) = app ?: return@withContext Result.failure(IOException("No Last.fm API key is set"))
        val signed = params + ("api_key" to key)
        val form = FormBody.Builder().apply {
            signed.forEach { (name, value) -> add(name, value) }
            add("api_sig", LastFmSignature.sign(signed, secret))
            add("format", "json")
        }.build()
        runCatching {
            http.newCall(Request.Builder().url(ENDPOINT).post(form).build()).execute().use { response ->
                val json = JSON.parseToJsonElement(response.body.string()) as? JsonObject
                    ?: throw IOException("Last.fm answered HTTP ${response.code}")
                json["error"]?.jsonPrimitive?.intOrNull?.let { code ->
                    throw LastFmError(code, json["message"]?.jsonPrimitive?.contentOrNull ?: "error $code")
                }
                if (!response.isSuccessful) throw IOException("Last.fm answered HTTP ${response.code}")
                json
            }
        }
    }

    /** One of Last.fm's numbered errors (last.fm/api/errorcodes). */
    class LastFmError(val code: Int, message: String) : IOException(message)

    companion object {
        /** Last.fm's own ceiling for one `track.scrobble`. */
        const val BATCH = 50
        private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
