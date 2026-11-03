package com.wander.android.data.sources.navidrome

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.repository.LrcParser
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException

/**
 * A song's lyrics from the server: OpenSubsonic's `getLyricsBySongId` where the server declares
 * the `songLyrics` extension, else the classic `getLyrics`, which matches by artist and title.
 *
 * Only the newer call existed before, so an older Navidrome, or any server implementing plain
 * Subsonic, never offered lyrics it held. Which one to use is read from the server's declared
 * extensions, asked once per server rather than learnt from a failed call on every song.
 *
 * A server that answers "no" — no lyrics, no such song — has answered: that is no lyrics, not an
 * outage. Only a transport failure stays a failure, so the panel can say it could not ask.
 */
internal class NavidromeLyrics(private val apiClient: SubsonicApiClient) {

    /** The server these extensions were read from, and their names. */
    @Volatile
    private var extensions: Pair<String, Set<String>>? = null

    suspend fun fetch(trackId: String, songId: String): Result<LyricsData?> {
        val declared = extensions().getOrElse { return Result.failure(it) }
        return if (SONG_LYRICS in declared) structured(trackId, songId) else classic(trackId, songId)
    }

    /**
     * A classic Subsonic server has no extensions endpoint, and says so either as a Subsonic
     * error or as an HTTP 404 whose body is no Subsonic response at all; both mean "none".
     */
    private suspend fun extensions(): Result<Set<String>> {
        val server = apiClient.serverKey
        extensions?.takeIf { it.first == server }?.let { return Result.success(it.second) }
        return apiClient.getOpenSubsonicExtensions()
            .map { list -> list.map { it.name }.toSet() }
            .recoverCatching { error ->
                when (error) {
                    is SubsonicServerException, is ContentConvertException, is NoTransformationFoundException -> emptySet()
                    else -> throw error
                }
            }
            .onSuccess { extensions = server to it }
    }

    private suspend fun structured(trackId: String, songId: String): Result<LyricsData?> =
        apiClient.getLyricsBySongId(songId).answered().map { list ->
            val structured = list?.structuredLyrics?.firstOrNull() ?: return@map null
            val lines = structured.line.orEmpty()
            if (lines.isEmpty()) return@map null
            LyricsData(
                trackId = trackId,
                isSynced = structured.synced,
                lines = lines.map { LyricLine(timestampMs = it.start ?: 0L, text = it.value) },
                source = SOURCE
            )
        }

    private suspend fun classic(trackId: String, songId: String): Result<LyricsData?> {
        val song = apiClient.getSong(songId).answered().getOrElse { return Result.failure(it) }
            ?: return Result.success(null)
        val artist = song.artist?.takeIf { it.isNotBlank() } ?: return Result.success(null)
        return apiClient.getLyrics(artist, song.title).answered().map { lyrics ->
            lyrics?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { classicLyrics(trackId, it) }
        }
    }

    internal companion object {
        private const val SONG_LYRICS = "songLyrics"
        private const val SOURCE = "Subsonic"

        /** A server's refusal as no answer at all; anything else is passed on as it was. */
        private fun <T> Result<T>.answered(): Result<T?> =
            recoverCatching { error -> if (error is SubsonicServerException) null else throw error }

        /** Classic lyrics are one text field, which some servers fill with LRC. */
        fun classicLyrics(trackId: String, text: String): LyricsData {
            val timed = LrcParser.parse(text)
            if (timed.isNotEmpty()) {
                return LyricsData(
                    trackId = trackId,
                    isSynced = true,
                    plainLyrics = timed.joinToString("\n") { it.text },
                    lines = timed,
                    source = SOURCE
                )
            }
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                .map { LyricLine(timestampMs = 0L, text = it) }.toList()
            return LyricsData(trackId = trackId, isSynced = false, plainLyrics = text, lines = lines, source = SOURCE)
        }
    }
}
