package com.wander.android.ui.components.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.wander.android.data.sources.StreamInfo
import kotlin.math.abs
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/** How far the picture may wander from the audio before it is put back. Under a lip-sync glance. */
private const val MAX_DRIFT_MS = 300L

/** How often drift is checked while playing. */
private const val DRIFT_CHECK_MS = 1_000L

/**
 * A corrective seek drops the picture to the nearest keyframe and rebuffers; spacing them out keeps
 * a slow connection from seeking again before the last one has caught up.
 */
private const val MIN_CORRECTION_GAP_MS = 3_000L

/**
 * A silent, picture-only player that follows the playback service's audio.
 *
 * The audio stays where it is — in the service, audio-only, offloadable — and this only paints the
 * music video over the cover while someone is looking at it. So it never makes a sound (volume 0,
 * audio renderers off), never sets its own position or pace, and mirrors [audio]'s play state,
 * seeks and speed. It is built through the shared [OkHttpClient], so the app's HTTPS rule applies to
 * the clip as it does to everything else.
 *
 * Owned by the composable that shows it and [release]d with it: the decoder exists only while the
 * clip is on screen.
 */
@OptIn(UnstableApi::class)
internal class VideoClipPlayer(
    context: Context,
    okHttpClient: OkHttpClient,
    stream: StreamInfo,
    private val audio: Player
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        volume = 0f
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        repeatMode = Player.REPEAT_MODE_OFF
        val dataSource = OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties(stream.headers)
        setMediaSource(
            ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(stream.uri))
        )
    }

    private var lastCorrectionAt = 0L

    private val follower = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            player.playWhenReady = isPlaying
            // A pause and resume leave both where they were; only re-seek if they had parted.
            if (abs(player.currentPosition - audio.currentPosition) > MAX_DRIFT_MS) alignPosition()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) alignPosition()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            player.playbackParameters = PlaybackParameters(playbackParameters.speed)
        }
    }

    init {
        player.playbackParameters = PlaybackParameters(audio.playbackParameters.speed)
        player.seekTo(audio.currentPosition)
        player.playWhenReady = audio.isPlaying
        player.prepare()
        audio.addListener(follower)
    }

    /** Runs for as long as the clip is shown, pulling the picture back whenever it drifts off. */
    suspend fun followDrift() {
        while (true) {
            delay(DRIFT_CHECK_MS)
            if (!audio.isPlaying || player.playbackState != Player.STATE_READY) continue
            val now = System.currentTimeMillis()
            if (now - lastCorrectionAt < MIN_CORRECTION_GAP_MS) continue
            if (abs(player.currentPosition - audio.currentPosition) > MAX_DRIFT_MS) {
                lastCorrectionAt = now
                player.seekTo(audio.currentPosition)
            }
        }
    }

    private fun alignPosition() {
        lastCorrectionAt = System.currentTimeMillis()
        player.seekTo(audio.currentPosition)
    }

    fun release() {
        audio.removeListener(follower)
        player.release()
    }
}
