package com.wander.android.data.repository

import android.util.LruCache
import com.wander.android.core.database.dao.EpisodeExtrasDao
import com.wander.android.core.database.entity.EpisodeExtrasEntity
import com.wander.android.data.podcast.Chapter
import com.wander.android.data.podcast.EpisodeChapters
import com.wander.android.data.podcast.TranscriptCue
import com.wander.android.data.podcast.TranscriptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chapters and transcripts of RSS episodes. The feed only gives addresses, so these are fetched when
 * the listener opens the panel, through the shared HTTPS-only client, and kept in memory for the
 * next opening. Nothing is written to disk and no URL is logged.
 */
@Singleton
class EpisodeExtrasRepository @Inject constructor(
    private val dao: EpisodeExtrasDao,
    private val client: OkHttpClient
) {
    private val chapterCache = LruCache<String, List<Chapter>>(CACHE_SIZE)
    private val transcriptCache = LruCache<String, List<TranscriptCue>>(CACHE_SIZE)

    fun observe(trackId: String): Flow<EpisodeExtrasEntity?> = dao.observe(trackId)

    suspend fun chapters(url: String): Result<List<Chapter>> {
        chapterCache.get(url)?.let { return Result.success(it) }
        return try {
            Result.success(EpisodeChapters.parse(download(url)).also { chapterCache.put(url, it) })
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun transcript(url: String, mime: String?): Result<List<TranscriptCue>> {
        transcriptCache.get(url)?.let { return Result.success(it) }
        val format = TranscriptParser.formatOf(mime)
            ?: return Result.failure(IOException("This transcript format is not supported"))
        return try {
            Result.success(TranscriptParser.parse(format, download(url)).also { transcriptCache.put(url, it) })
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The server answered HTTP ${response.code}")
            val source = response.body.source()
            source.request(MAX_BYTES + 1)
            if (source.buffer.size > MAX_BYTES) throw IOException("The file is larger than 4 MB")
            source.readUtf8()
        }
    }

    private companion object {
        const val CACHE_SIZE = 6
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
