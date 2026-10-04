package com.wander.android.core.playback

import androidx.media3.common.Player
import com.wander.android.data.repository.ScrobbleForwarding
import com.wander.android.data.sources.scrobble.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tells ListenBrainz and Last.fm what is playing, once a track has been playing for a few seconds.
 *
 * The wait is what keeps skipping through a queue from sending one request per track flicked
 * past. Each track is announced once; pausing and resuming it does not announce it again. Live
 * streams are not announced, as they are not recorded as plays either.
 */
internal class NowPlayingForwarder(
    private val player: Player,
    private val scope: CoroutineScope,
    private val forwarding: ScrobbleForwarding
) : Player.Listener {

    private var announced: String? = null
    private var pending: Job? = null

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = consider()

    override fun onIsPlayingChanged(isPlaying: Boolean) = consider()

    private fun consider() {
        pending?.cancel()
        if (!player.isPlaying || !forwarding.isAnyOn) return
        val track = player.currentMediaItem?.toUnifiedTrack() ?: return
        if (track.isLive || track.id == announced) return
        pending = scope.launch {
            delay(SETTLE_MS)
            announced = track.id
            forwarding.nowPlaying(NowPlaying(track.title, track.artist, track.album, track.durationMs))
        }
    }

    private companion object {
        const val SETTLE_MS = 5_000L
    }
}
