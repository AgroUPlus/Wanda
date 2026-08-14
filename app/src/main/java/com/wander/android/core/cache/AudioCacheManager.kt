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

    fun getCacheDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun cacheSizeBytes(): Long = simpleCache.cacheSpace

    fun clearCache() {
        simpleCache.keys.toList().forEach { simpleCache.removeResource(it) }
    }

    private companion object {
        const val DEFAULT_MAX_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB
    }
}
