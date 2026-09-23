package com.wander.android.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Turns Media3's silence skipping on for podcast episodes only.
 *
 * It is a player-wide switch, so it is re-decided on every item change. Music never gets it: a
 * fade-in, a pause before the last chorus, the gap on a live album are all silence that is meant
 * to be there, and trimming them is breaking the record rather than saving time.
 */
internal class EpisodeSkipSilence(
    private val player: ExoPlayer,
    private val enabled: StateFlow<Boolean>,
    scope: CoroutineScope
) : Player.Listener {

    init {
        player.addListener(this)
        scope.launch { enabled.collect { apply() } }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = apply()

    private fun apply() {
        val isEpisode = player.currentMediaItem?.toUnifiedTrack()?.isEpisode == true
        player.skipSilenceEnabled = enabled.value && isEpisode
    }
}
