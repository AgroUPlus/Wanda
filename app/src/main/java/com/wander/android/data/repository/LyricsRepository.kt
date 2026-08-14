package com.wander.android.data.repository

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.sources.IMusicSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.parameter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LrclibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@Singleton
class LyricsRepository @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards IMusicSource>,
    private val client: HttpClient
) {
    private val lrclibBaseUrl = "https://lrclib.net/api/get"

    suspend fun getLyrics(
        trackId: String,
        trackTitle: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Long? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        // Step 1: Check source-native lyrics (e.g. Navidrome embedded synced lyrics)
        val matchingSource = sources.firstOrNull {
            it.capabilities.lyrics && trackId.startsWith(it.sourceType.idPrefix)
        }
        val nativeLyrics = matchingSource?.getLyrics(trackId)?.getOrNull()
        if (nativeLyrics != null && nativeLyrics.lines.isNotEmpty()) {
            return@withContext nativeLyrics
        }

        // Step 2: Fallback to privacy-friendly LRCLIB
        try {
            val response: LrclibResponse = client.get(lrclibBaseUrl) {
                parameter("track_name", trackTitle)
                parameter("artist_name", artistName)
                if (!albumName.isNullOrBlank()) parameter("album_name", albumName)
                if (durationSeconds != null && durationSeconds > 0) parameter("duration", durationSeconds)
            }.body()

            if (!response.syncedLyrics.isNullOrBlank()) {
                val lines = parseLrc(response.syncedLyrics)
                return@withContext LyricsData(
                    trackId = trackId,
                    isSynced = true,
                    plainLyrics = response.plainLyrics,
                    lines = lines,
                    source = "LRCLIB"
                )
            } else if (!response.plainLyrics.isNullOrBlank()) {
                return@withContext LyricsData(
                    trackId = trackId,
                    isSynced = false,
                    plainLyrics = response.plainLyrics,
                    source = "LRCLIB (Plain)"
                )
            }
        } catch (e: ClientRequestException) {
            // 404 just means LRCLIB has no match for this track; the UI shows no lyrics.
        } catch (e: IOException) {
            // Offline or DNS failure. Same outcome, nothing to report.
        }

        null
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val lrcRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        lrcContent.lineSequence().forEach { lineText ->
            val match = lrcRegex.find(lineText.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val fractionMs = if (fractionStr.length == 2) {
                    (fractionStr.toLongOrNull() ?: 0L) * 10
                } else {
                    fractionStr.toLongOrNull() ?: 0L
                }
                val totalMs = (min * 60 * 1000) + (sec * 1000) + fractionMs
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timestampMs = totalMs, text = text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
