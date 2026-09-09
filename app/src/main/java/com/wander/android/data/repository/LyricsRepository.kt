package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricMatch
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
    private val trackLyricsDao: TrackLyricsDao,
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
        // Step 1: Check offline Room database (source-agnostic: by trackId or by title & artist)
        val cached = trackLyricsDao.findLyricsForTrackOrMetadata(trackId, trackTitle.trim(), artistName.trim())
            ?: trackLyricsDao.getLyricsForTrack(trackId)
        if (cached != null) {
            if (cached.trackId != trackId) {
                // Link these lyrics to the current trackId so future lookups are instant
                trackLyricsDao.saveLyricsWithFts(cached.copy(trackId = trackId))
            }
            if (!cached.syncedLyrics.isNullOrBlank()) {
                val lines = parseLrc(cached.syncedLyrics)
                return@withContext LyricsData(
                    trackId = trackId,
                    isSynced = true,
                    plainLyrics = cached.plainLyrics,
                    lines = lines,
                    source = cached.source
                )
            } else if (cached.plainLyrics.isNotBlank()) {
                return@withContext LyricsData(
                    trackId = trackId,
                    isSynced = false,
                    plainLyrics = cached.plainLyrics,
                    source = cached.source
                )
            }
        }

        // Step 2: Check source-native lyrics (e.g. Navidrome embedded synced lyrics)
        val matchingSource = sources.firstOrNull {
            it.capabilities.lyrics && trackId.startsWith(it.sourceType.idPrefix)
        }
        val nativeLyrics = matchingSource?.getLyrics(trackId)?.getOrNull()
        if (nativeLyrics != null && nativeLyrics.lines.isNotEmpty()) {
            val plain = nativeLyrics.plainLyrics ?: nativeLyrics.lines.joinToString("\n") { it.text }
            trackLyricsDao.saveLyricsWithFts(
                TrackLyricsEntity(
                    trackId = trackId,
                    plainLyrics = plain,
                    syncedLyrics = null,
                    source = nativeLyrics.source ?: "Native"
                )
            )
            return@withContext nativeLyrics
        }

        // Step 3: Fallback to privacy-friendly LRCLIB
        try {
            val response: LrclibResponse = client.get(lrclibBaseUrl) {
                parameter("track_name", trackTitle)
                parameter("artist_name", artistName)
                if (!albumName.isNullOrBlank()) parameter("album_name", albumName)
                if (durationSeconds != null && durationSeconds > 0) parameter("duration", durationSeconds)
            }.body()

            if (!response.syncedLyrics.isNullOrBlank()) {
                val lines = parseLrc(response.syncedLyrics)
                val plain = response.plainLyrics ?: lines.joinToString("\n") { it.text }
                trackLyricsDao.saveLyricsWithFts(
                    TrackLyricsEntity(
                        trackId = trackId,
                        plainLyrics = plain,
                        syncedLyrics = response.syncedLyrics,
                        source = "LRCLIB"
                    )
                )
                return@withContext LyricsData(
                    trackId = trackId,
                    isSynced = true,
                    plainLyrics = plain,
                    lines = lines,
                    source = "LRCLIB"
                )
            } else if (!response.plainLyrics.isNullOrBlank()) {
                trackLyricsDao.saveLyricsWithFts(
                    TrackLyricsEntity(
                        trackId = trackId,
                        plainLyrics = response.plainLyrics,
                        syncedLyrics = null,
                        source = "LRCLIB (Plain)"
                    )
                )
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

    suspend fun searchByLyrics(query: String, limit: Int = 25): List<LyricMatch> = withContext(Dispatchers.IO) {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        val ftsQuery = words.mapIndexed { idx, term ->
            val clean = term.replace(Regex("[^\\p{L}\\p{Nd}]"), "")
            if (idx == words.size - 1) "$clean*" else clean
        }.filter { it.isNotBlank() && it != "*" }.joinToString(" ")
        if (ftsQuery.isBlank()) return@withContext emptyList()

        try {
            val results = trackLyricsDao.searchTracksByLyrics(ftsQuery, limit * 2)
            val matches = results.map { res ->
                val (line, ts) = findMatchingLine(res.plainLyrics, res.syncedLyrics, query)
                LyricMatch(
                    track = res.track.toUnifiedTrack(),
                    matchedLine = line,
                    snippet = res.snippet,
                    timestampMs = ts,
                    source = "LyricsFTS"
                )
            }
            // Deduplicate across sources (Local > Navidrome > YTM) using canonical recordingKey
            val seenKeys = mutableSetOf<String>()
            val deduplicated = mutableListOf<LyricMatch>()
            for (match in matches.sortedBy { it.track.source.priority }) {
                val key = TrackDeduplicator.recordingKey(match.track)
                if (seenKeys.add(key)) {
                    deduplicated.add(match)
                }
                if (deduplicated.size >= limit) break
            }
            deduplicated
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findMatchingLine(
        plainLyrics: String,
        syncedLyrics: String?,
        query: String
    ): Pair<String, Long?> {
        val cleanQuery = query.trim().lowercase()
        if (!syncedLyrics.isNullOrBlank()) {
            val lines = parseLrc(syncedLyrics)
            val match = lines.firstOrNull { it.text.lowercase().contains(cleanQuery) }
                ?: lines.firstOrNull { l ->
                    val lineWords = l.text.lowercase().split(Regex("\\W+"))
                    cleanQuery.split(Regex("\\W+")).all { w -> lineWords.contains(w) }
                }
            if (match != null) {
                return Pair(match.text, match.timestampMs)
            }
        }
        val plainLine = plainLyrics.lineSequence().firstOrNull { it.lowercase().contains(cleanQuery) }
            ?: plainLyrics.lineSequence().firstOrNull { l ->
                val lineWords = l.lowercase().split(Regex("\\W+"))
                cleanQuery.split(Regex("\\W+")).all { w -> lineWords.contains(w) }
            }
            ?: plainLyrics.lineSequence().firstOrNull() ?: ""
        return Pair(plainLine.trim(), null)
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
