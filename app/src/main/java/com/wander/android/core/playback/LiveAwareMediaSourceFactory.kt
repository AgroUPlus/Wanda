package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Sends livestreams down an uncached path and everything else down the cached one.
 *
 * The cache is right for a recording and wrong for a broadcast. A live HLS media playlist is
 * re-fetched at an unchanging URL every few seconds — that is how the player learns which segments
 * exist now — so a cache in front of it answers every reload with the copy it took the first time.
 * The playlist then never advances, and the player gives up with `PlaylistStuckException` a few
 * seconds in, recovers, and does it again: the stream stopping and restarting every twenty seconds
 * or so is exactly this.
 *
 * Media3 has no per-request opt-out of a `CacheDataSource`, so the split is made here, where the
 * media source is built and the item's container is already known.
 */
@OptIn(UnstableApi::class)
internal class LiveAwareMediaSourceFactory(
    private val cached: MediaSource.Factory,
    private val uncachedHls: HlsMediaSource.Factory
) : MediaSource.Factory {

    override fun getSupportedTypes(): IntArray = cached.supportedTypes

    override fun setDrmSessionManagerProvider(
        provider: DrmSessionManagerProvider
    ): MediaSource.Factory = apply {
        cached.setDrmSessionManagerProvider(provider)
        uncachedHls.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(
        policy: LoadErrorHandlingPolicy
    ): MediaSource.Factory = apply {
        cached.setLoadErrorHandlingPolicy(policy)
        uncachedHls.setLoadErrorHandlingPolicy(policy)
    }

    override fun setCmcdConfigurationFactory(
        factory: CmcdConfiguration.Factory
    ): MediaSource.Factory = apply { cached.setCmcdConfigurationFactory(factory) }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (mediaItem.isHlsItem()) {
            uncachedHls.createMediaSource(mediaItem)
        } else {
            cached.createMediaSource(mediaItem)
        }
}

/**
 * Both hints [toMediaItem] sets, checked the same way the media-source factory would.
 *
 * The MIME type is preferred and the suffix is the fallback, because the MIME type lives in
 * `localConfiguration` and does not always survive the trip across IPC to the playback service.
 */
private fun MediaItem.isHlsItem(): Boolean =
    localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8 ||
        localConfiguration?.uri?.toString()?.endsWith(LIVE_SUFFIX) == true
