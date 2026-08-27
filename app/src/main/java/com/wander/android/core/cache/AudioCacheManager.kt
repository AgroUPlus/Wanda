package com.wander.android.core.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

/**
 * Streaming cache. Anything already heard is replayed from disk instead of being re-fetched,
 * which saves both data and radio wakeups.
 */
@OptIn(UnstableApi::class)
class AudioCacheManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    maxSizeBytes: Long = DEFAULT_MAX_SIZE
) {
    private val cacheDir = File(context.cacheDir, "audio_stream_cache")

    val simpleCache: SimpleCache by lazy {
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(maxSizeBytes),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * The plain network path, with no cache in front of it.
     *
     * Livestreams use this one. A live HLS media playlist is re-fetched every few seconds at the
     * *same URL* to pick up new segments, so putting it behind a cache means the first copy is
     * served forever and the playlist never advances — which ExoPlayer reports, correctly, as
     * `PlaylistStuckException`. Its segments have nothing to gain from a cache either: each is
     * played once and never referenced again, so caching them only evicts music somebody might
     * actually replay.
     */
    fun getUpstreamDataSourceFactory(): DataSource.Factory =
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))

    fun getCacheDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(getUpstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun cacheSizeBytes(): Long = simpleCache.cacheSpace

    fun clearCache() {
        simpleCache.keys.toList().forEach { simpleCache.removeResource(it) }
    }

    private companion object {
        const val DEFAULT_MAX_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB
    }
}
