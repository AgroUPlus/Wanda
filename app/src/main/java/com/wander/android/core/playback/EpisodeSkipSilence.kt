package com.wander.android.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wander.android.data.model.UnifiedTrack
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
        player.skipSilenceEnabled = skipsSilence(enabled.value, player.currentMediaItem?.toUnifiedTrack())
    }
}

/**
 * Whether [track] plays with silence skipped, given the setting is [enabled].
 *
 * Shared with the offload decision in [PlayerConnection], because skipping silence is an audio
 * processor and offload bypasses every one of them: with both on, the setting silently did nothing.
 * See [offloadAllowed].
 */
internal fun skipsSilence(enabled: Boolean, track: UnifiedTrack?): Boolean =
    enabled && track?.isEpisode == true
