package com.wander.android.core.p2p

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads what the peer at the other end of a radio link is playing.
 *
 * Polled rather than pushed, and that is a deliberate downgrade from how Agro does it. A push needs
 * a socket held open in both directions and a reconnect policy for a link that drops whenever a
 * screen sleeps; a poll over a link that is already up costs one small request every couple of
 * seconds and has no state to get wrong. The tier exists for two phones in a car, not for a
 * thousand listeners.
 */
@Singleton
internal class OffGridNowPlayingClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One reading, or null when the peer could not be reached or did not answer with one.
     *
     * Null is not an error worth surfacing on its own — a single missed poll over a radio link is
     * ordinary. The caller decides how many in a row mean the peer has gone.
     */
    suspend fun read(baseUrl: String, grantToken: String): OffGridNowPlaying? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/p2p/now-playing")
                    .header("Authorization", "Bearer $grantToken")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    json.decodeFromString(OffGridNowPlaying.serializer(), body)
                }
            }.getOrNull()
        }

    private companion object {
        /** Short: the peer is one radio hop away, and a poll that outlives its interval is noise. */
        const val TIMEOUT_MS = 3_000L
    }
}
