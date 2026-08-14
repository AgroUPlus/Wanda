package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a `wanda://track/<id>` placeholder into the real stream URL and headers, at the moment
 * the loader opens it. Runs on ExoPlayer's loading thread, so blocking here is correct.
 */
@Singleton
@OptIn(UnstableApi::class)
class StreamResolver @Inject constructor(
    private val musicRepository: MusicRepository
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val trackId = dataSpec.uri.wandaTrackId() ?: return dataSpec

        val streamInfo = runBlocking { musicRepository.getStreamInfo(trackId) }
            .getOrElse { throw IOException("Could not resolve stream for track", it) }

        return dataSpec
            .buildUpon()
            .setUri(streamInfo.uri.toUri())
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + streamInfo.headers)
            .build()
    }
}
