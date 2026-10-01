package com.wander.android

import android.app.Application
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.launch
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.database.DatabaseCompatibility
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.sync.ScrobbleSyncScheduler
import com.zemer.cipher.ZemerCipher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Dependencies are wired by Hilt (see `di/`). The previous hand-rolled service locator built
 * everything eagerly on the main thread at startup and could not inject the WorkManager worker.
 */
@HiltAndroidApp
class WanderApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloadScheduler: DownloadScheduler
    @Inject lateinit var scrobbleSyncScheduler: ScrobbleSyncScheduler
    @Inject lateinit var podcastSyncScheduler: com.wander.android.core.work.PodcastSyncScheduler
    @Inject lateinit var librarySyncScheduler: com.wander.android.core.sync.LibrarySyncScheduler
    @Inject lateinit var p2pServer: com.wander.android.core.sync.P2PServer
    @Inject lateinit var secureStorage: com.wander.android.core.security.SecureStorage
    @Inject lateinit var likedTrackCacheProtector: com.wander.android.data.repository.LikedTrackCacheProtector

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * No loader-wide crossfade: it re-runs a frame-invalidating animation for every artwork
     * entering a list, memory-cache hits included, which is what made Home and Library stutter.
     * The full-screen player opts in per request instead (see [com.wander.android.ui.components.Artwork]).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Share the app's client so artwork reuses one connection pool and the
                // default User-Agent interceptor, instead of Coil spinning up its own.
                add(OkHttpNetworkFetcherFactory(callFactory = { HttpClientFactory.okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(imageDiskCacheBytes(context))
                    .build()
            }
            .build()
    }

    /**
     * A percentage of free space, the same idea [MemoryCache.Builder.maxSizePercent] already
     * applies above — a fixed 100MB was the same tiny slice of a 16GB phone and a 1TB tablet, so
     * heavy libraries on capable devices were re-fetching artwork a bigger cache would have kept.
     * Bounded on both ends: a phone nearly out of space still gets a floor worth having, and a
     * phone with a completely empty drive does not get asked to give away most of it to album art.
     */
    private fun imageDiskCacheBytes(context: PlatformContext): Long {
        val availableBytes = runCatching {
            val stats = android.os.StatFs(context.cacheDir.path)
            stats.blockSizeLong * stats.availableBlocksLong
        }.getOrDefault(0L)
        return (availableBytes / 50) // 2% of free space
            .coerceIn(MIN_IMAGE_CACHE_BYTES, MAX_IMAGE_CACHE_BYTES)
    }

    /** Outlives every screen, like the server it starts. */
    private val applicationScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )

    override fun onCreate() {
        super.onCreate()
        // A database too old for any migration cannot be opened, and everything below either
        // queries it or starts something that does. MainActivity explains the situation instead.
        if (DatabaseCompatibility.isTooOld(this)) return
        downloadScheduler.scheduleAutoDownload()
        com.wander.android.core.audio.fingerprint.FingerprintIndexing.schedulePeriodic(
            this,
            allowMobileData = secureStorage.isIndexOnMobileDataEnabled.value
        )
        // Cheap and self-gating: the worker does nothing until an Agro server is paired.
        scrobbleSyncScheduler.schedule()
        // KEEP, and a pass with no subscriptions does nothing, so this is also what restores syncing
        // after a backup brings subscriptions back onto a fresh install.
        podcastSyncScheduler.enable()
        likedTrackCacheProtector.start(applicationScope)
        if (secureStorage.agroCatalogTrade || secureStorage.agroP2pSync || secureStorage.agroServerArchive) {
            librarySyncScheduler.enablePeriodicSync()
        }
        // Embedded P2P server for direct high-speed LAN audio transfers.
        //
        // Launched rather than awaited: `onCreate` must not block on a bind, and nothing on this
        // path has a screen to report to. The off-grid screen starts it again and *does* wait,
        // which is where a taken port becomes something the user is told about.
        //
        // Follows the P2P sync setting rather than starting unconditionally. It was the only thing
        // this switch did not reach, so a user who turned device-to-device transfers off still had
        // a port bound and accepting for the life of the process.
        applicationScope.launch {
            secureStorage.agroP2pSyncFlow.collect { enabled ->
                if (enabled) p2pServer.start() else p2pServer.stop()
            }
        }
        // Needed for YT Music's PO Token / signature-cipher deobfuscation (see InnerTubeClient).
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ZemerCipher.initialize(context = this, debugLogging = isDebuggable)
        // Lets a debug build's importer WebView (Spotify/Deezer/YouTube sign-in) be inspected live
        // from chrome://inspect on a connected machine — the only way to see a page that renders
        // nothing and logs nothing itself, which `console.*`-only logging can't catch.
        android.webkit.WebView.setWebContentsDebuggingEnabled(isDebuggable)
    }

    private companion object {
        const val MIN_IMAGE_CACHE_BYTES = 100L * 1024 * 1024 // the old flat size, as a floor
        const val MAX_IMAGE_CACHE_BYTES = 1024L * 1024 * 1024
    }
}

