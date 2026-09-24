package com.wander.android.data.repository

import android.content.ContentUris
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.sync.MediaStoreWriter
import com.wander.android.core.sync.WriteResult
import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.agro.AgroLibraryApi
import com.wander.android.data.sources.agro.AgroUploader
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.local.LocalMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads missing tracks from peer devices or Agro relay and saves them to local MediaStore.
 */
@Singleton
internal class LibraryMissingFetcher @Inject constructor(
    private val trackDao: TrackDao,
    private val libraryApi: AgroLibraryApi,
    private val uploader: AgroUploader,
    private val mediaStoreWriter: MediaStoreWriter,
    private val localSource: LocalMusicSource
) {
    suspend fun fetchMissing(
        tracks: List<MissingTrack>,
        onProgress: (FetchProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var fetched = 0
        val done = mutableSetOf<String>()
        val arrivedHashes = mutableListOf<Pair<String, String>>()
        for (track in tracks) {
            onProgress(FetchProgress(done.toSet(), track.contentHash, tracks.size, null))
            val stream = uploader.fetchP2POrRelay(track).getOrElse { error ->
                return@withContext if (fetched > 0) Result.success(fetched) else Result.failure(error)
            }
            onProgress(FetchProgress(done.toSet(), track.contentHash, tracks.size, stream.route))
            val written = stream.response.use { body ->
                mediaStoreWriter.write(
                    source = body.body.byteStream(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    extension = track.format?.takeIf { it.isNotBlank() } ?: "flac",
                    expectedHash = track.contentHash
                )
            }
            val problem = when (written) {
                is WriteResult.Written -> null
                WriteResult.Empty ->
                    "Nothing arrived for \"${track.title}\" — the device holding it did not send."
                WriteResult.HashMismatch -> "\"${track.title}\" arrived corrupted."
                is WriteResult.Failed -> "Couldn't save \"${track.title}\": ${written.reason}."
            }
            if (problem != null) {
                return@withContext if (fetched > 0) {
                    Result.success(fetched)
                } else {
                    Result.failure(IOException(problem))
                }
            }
            fetched++
            done += track.contentHash
            (written as? WriteResult.Written)?.let { result ->
                libraryApi.reportFetchedHolding(track, result.uri.toString())
                arrivedHashes += "${SourceType.LOCAL.idPrefix}${ContentUris.parseId(result.uri)}" to
                    track.contentHash
            }
            onProgress(FetchProgress(done.toSet(), null, tracks.size, null))
        }
        if (arrivedHashes.isNotEmpty()) {
            localSource.refresh()
            arrivedHashes.forEach { (id, hash) -> trackDao.setContentHash(id, hash) }
        }
        Result.success(fetched)
    }
}
