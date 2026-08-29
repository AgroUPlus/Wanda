package com.wander.android

import android.app.Application
import android.content.pm.ApplicationInfo
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
    @Inject lateinit var p2pServer: com.wander.android.core.sync.P2PServer

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
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        downloadScheduler.scheduleAutoDownload()
        // Cheap and self-gating: the worker does nothing until an Agro server is paired.
        scrobbleSyncScheduler.schedule()
        // Embedded P2P server for direct high-speed LAN audio transfers
        p2pServer.start()
        // Needed for YT Music's PO Token / signature-cipher deobfuscation (see InnerTubeClient).
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ZemerCipher.initialize(context = this, debugLogging = isDebuggable)
    }
}

