package com.wander.android.data.sources.deezer

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Deezer track's lyrics, as the signed-in account's gateway session serves them.
 *
 * Deezer refuses the call outright for a song it holds no lyrics for. That refusal is an answer —
 * no lyrics — and comes back as null, as Navidrome's "not found" does; only a session that is
 * still expired after one fresh attempt, or a transport failure, stays a failure.
 */
@Singleton
class DeezerLyrics @Inject constructor(
    private val gateway: DeezerGateway,
    private val accountManager: DeezerAccountManager
) {
    suspend fun fetch(trackId: String): Result<LyricsData?> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix(PREFIX)
        val arl = accountManager.arl
        if (arl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Deezer lyrics require signing in"))
        }
        var session = gateway.session(arl).getOrElse { return@withContext Result.failure(it) }
        var answer = gateway.songLyrics(session, cleanId)
        if ((answer.exceptionOrNull() as? DeezerGateway.GatewayException)?.isSessionExpired == true) {
            gateway.invalidate()
            session = gateway.session(arl).getOrElse { return@withContext Result.failure(it) }
            answer = gateway.songLyrics(session, cleanId)
        }
        answer.fold(
            onSuccess = { Result.success(parse(trackId, it)) },
            onFailure = { error ->
                val refused = error as? DeezerGateway.GatewayException
                if (refused != null && !refused.isSessionExpired) Result.success(null) else Result.failure(error)
            }
        )
    }

    internal companion object {
        private const val PREFIX = "deezer:"
        const val SOURCE = "Deezer"

        /**
         * Timed lines from `LYRICS_SYNC_JSON` when there are any — each with `milliseconds` and
         * `line`, the empty ones marking instrumental gaps — else `LYRICS_TEXT` as untimed lines.
         */
        fun parse(trackId: String, results: JsonObject): LyricsData? {
            val plain = text(results["LYRICS_TEXT"] as? JsonPrimitive)
                ?.replace("\r\n", "\n")?.trim()?.takeIf { it.isNotEmpty() }
            val timed = (results["LYRICS_SYNC_JSON"] as? JsonArray).orEmpty().mapNotNull { entry ->
                val fields = entry as? JsonObject ?: return@mapNotNull null
                val line = text(fields["line"] as? JsonPrimitive)?.trim()?.takeIf { it.isNotEmpty() }
                val start = text(fields["milliseconds"] as? JsonPrimitive)?.toLongOrNull()
                if (line == null || start == null) null else LyricLine(timestampMs = start, text = line)
            }.sortedBy { it.timestampMs }
            if (timed.isNotEmpty()) {
                return LyricsData(
                    trackId = trackId,
                    isSynced = true,
                    plainLyrics = plain ?: timed.joinToString("\n") { it.text },
                    lines = timed,
                    source = SOURCE
                )
            }
            plain ?: return null
            val untimed = plain.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                .map { LyricLine(timestampMs = 0L, text = it) }.toList()
            return LyricsData(trackId = trackId, isSynced = false, plainLyrics = plain, lines = untimed, source = SOURCE)
        }

        /** Deezer sends `milliseconds` as a string on some songs and a number on others. */
        private fun text(value: JsonPrimitive?): String? = value?.contentOrNull
    }
}
