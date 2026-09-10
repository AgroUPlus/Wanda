package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricMatch
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.LyricsState
import com.wander.android.data.sources.IMusicSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.parameter
import android.database.sqlite.SQLiteException
import android.util.Log
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

    companion object {
        private const val TAG = "LyricsRepository"

        /**
         * How long a "there are none" answer is trusted before asking again.
         *
         * Long, because the answer rarely changes: LRCLIB gaining lyrics for a track that had none
         * is a real event but a slow one, and the cost of being a month late is a month of correct
         * offline behaviour.
         */
        private const val ABSENCE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** `source` values that mark a textless row rather than name where lyrics came from. */
        private const val ABSENT_SOURCE = "None"
        private const val INSTRUMENTAL_SOURCE = "Instrumental"
    }

    suspend fun getLyrics(
        trackId: String,
        trackTitle: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Long? = null
    ): LyricsState = withContext(Dispatchers.IO) {
        // Step 1: Check offline Room database (source-agnostic: by trackId or by title & artist)
        val cached = trackLyricsDao.findLyricsForTrackOrMetadata(trackId, trackTitle.trim(), artistName.trim())
            ?: trackLyricsDao.getLyricsForTrack(trackId)
        if (cached != null) {
            if (cached.trackId != trackId && cached.plainLyrics.isNotBlank()) {
                // Link these lyrics to the current trackId so future lookups are instant,
                // then drop the old row so it doesn't linger as a duplicate search hit.
                //
                // Textless rows are left where they are: they carry a negative answer, not lyrics,
                // and relinking one would put a contentless entry into the search index.
                val staleTrackId = cached.trackId
                trackLyricsDao.saveLyricsWithFts(cached.copy(trackId = trackId))
                trackLyricsDao.deleteLyricsWithFts(staleTrackId)
            }
            if (!cached.syncedLyrics.isNullOrBlank()) {
                val lines = parseLrc(cached.syncedLyrics)
                return@withContext LyricsState.Present(
                    LyricsData(
                        trackId = trackId,
                        isSynced = true,
                        plainLyrics = cached.plainLyrics,
                        lines = lines,
                        source = cached.source
                    )
                )
            } else if (cached.plainLyrics.isNotBlank()) {
                return@withContext LyricsState.Present(
                    LyricsData(
                        trackId = trackId,
                        isSynced = false,
                        plainLyrics = cached.plainLyrics,
                        source = cached.source
                    )
                )
            }
            // A row with no text is a remembered negative answer. Re-ask only once it has aged
            // out: a track can acquire lyrics upstream, but not often enough to pay for a request
            // on every panel open.
            val absentSince = cached.absentSince
            if (absentSince != null && System.currentTimeMillis() - absentSince < ABSENCE_TTL_MS) {
                return@withContext if (cached.source == INSTRUMENTAL_SOURCE) {
                    LyricsState.Instrumental
                } else {
                    LyricsState.Absent
                }
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
            return@withContext LyricsState.Present(nativeLyrics)
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
                return@withContext LyricsState.Present(
                    LyricsData(
                        trackId = trackId,
                        isSynced = true,
                        plainLyrics = plain,
                        lines = lines,
                        source = "LRCLIB"
                    )
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
                return@withContext LyricsState.Present(
                    LyricsData(
                        trackId = trackId,
                        isSynced = false,
                        plainLyrics = response.plainLyrics,
                        source = "LRCLIB (Plain)"
                    )
                )
            }
            // Answered, with nothing in it. LRCLIB knows this recording and is telling us it has
            // no words, or none it holds — either way that is an answer worth remembering.
            return@withContext if (response.instrumental) {
                rememberAbsence(trackId, INSTRUMENTAL_SOURCE)
                LyricsState.Instrumental
            } else {
                rememberAbsence(trackId, ABSENT_SOURCE)
                LyricsState.Absent
            }
        } catch (e: ClientRequestException) {
            // 404 is LRCLIB saying it has no match, which is a fact about the track and keeps.
            rememberAbsence(trackId, ABSENT_SOURCE)
            return@withContext LyricsState.Absent
        } catch (e: IOException) {
            // Offline or DNS failure: the question never got asked. Record nothing, so the next
            // attempt still tries, and say so rather than claiming the track has no lyrics.
            Log.i(TAG, "Could not reach LRCLIB: ${e.javaClass.simpleName}")
            return@withContext LyricsState.Unreachable
        }
    }

    /**
     * Writes the textless row that says "asked, and there are none".
     *
     * It deliberately does not go through `saveLyricsWithFts`: there is nothing to index, and an
     * empty FTS entry would put a contentless hit in front of lyric search.
     */
    private suspend fun rememberAbsence(trackId: String, source: String) {
        trackLyricsDao.insertLyrics(
            TrackLyricsEntity(
                trackId = trackId,
                plainLyrics = "",
                syncedLyrics = null,
                source = source,
                absentSince = System.currentTimeMillis()
            )
        )
        trackLyricsDao.deleteFts(trackId)
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
        } catch (e: SQLiteException) {
            Log.w(TAG, "Lyric search query failed: ${e.message}")
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
