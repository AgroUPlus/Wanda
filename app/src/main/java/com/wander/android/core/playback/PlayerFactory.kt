package com.wander.android.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.wander.android.core.cache.AudioCacheManager
import javax.inject.Inject

/**
 * Builds the one and only [ExoPlayer]. Owned by [PlaybackService]; nothing else constructs a
 * player.
 */
@OptIn(UnstableApi::class)
class PlayerFactory @Inject constructor(
    private val context: Context,
    private val cacheManager: AudioCacheManager,
    private val streamResolver: StreamResolver,
    private val pcmTap: PcmTap
) {

    fun create(): ExoPlayer {
        val upstream: DataSource.Factory = ResolvingDataSource.Factory(
            cacheManager.getCacheDataSourceFactory(),
            streamResolver
        )
        // The same resolver, without the cache behind it — see [LiveAwareMediaSourceFactory].
        val liveUpstream: DataSource.Factory = ResolvingDataSource.Factory(
            cacheManager.getUpstreamDataSourceFactory(),
            streamResolver
        )
        val mediaSourceFactory = LiveAwareMediaSourceFactory(
            cached = DefaultMediaSourceFactory(upstream),
            uncachedHls = HlsMediaSource.Factory(liveUpstream)
                // A live window moves on whether or not this device kept up. Letting the player
                // re-join at the edge is what the chunkless preparation and the rolling playlist
                // are for; refusing to would strand it behind a window that no longer exists.
                .setAllowChunklessPreparation(true)
        )

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                // The tee passes audio through untouched and copies it to the FFT processor.
                .setAudioProcessors(arrayOf(TeeAudioProcessor(pcmTap)))
                .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { trackSelectionParameters = withOffload(trackSelectionParameters, enabled = true) }
    }

    companion object {
        /**
         * Audio offload lets the DSP play the stream while the CPU sleeps — a large battery win —
         * but decoded PCM never reaches the processor chain, so the visualizer goes silent.
         * Callers flip this off while a visualizer is on screen.
         */
        fun withOffload(
            parameters: androidx.media3.common.TrackSelectionParameters,
            enabled: Boolean
        ): androidx.media3.common.TrackSelectionParameters = parameters.buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (enabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .setIsGaplessSupportRequired(true)
                    .build()
            )
            .build()

        /**
         * Keeps a livestream to audio, at the smallest rendition that carries it.
         *
         * YouTube serves these as HLS variants that are all muxed video+audio — there is no
         * audio-only rendition to pick — so left alone the player selected a video track and span
         * up an H.264 decoder to render pictures nobody can see, in a music app, off a phone
         * battery. Worse, its adaptive selection climbed towards the best *video* quality: 5.4 Mbps
         * for a stream whose audio is 128 kbps of it.
         *
         * Disabling the track type rather than constraining its size, because the selector is
         * allowed to exceed size constraints when nothing satisfies them — and with only muxed
         * variants on offer, nothing does. Forcing the lowest bitrate is what then picks the
         * smallest variant, since bandwidth is the only axis left that means anything here.
         */
        fun withVideoSuppressed(
            parameters: androidx.media3.common.TrackSelectionParameters,
            suppressed: Boolean
        ): androidx.media3.common.TrackSelectionParameters = parameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, suppressed)
            .setForceLowestBitrate(suppressed)
            .build()
    }
}
