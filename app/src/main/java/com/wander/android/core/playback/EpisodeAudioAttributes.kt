package com.wander.android.core.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Tells the system a podcast episode is speech, and everything else music.
 *
 * The content type decides what happens when another app briefly takes audio focus — a navigation
 * prompt, a notification sound. Music ducks under it and carries on, which is right for a song.
 * Speech pauses instead: talking over a sentence at a fifth of the volume loses the sentence, and
 * Media3 pauses rather than ducks only for [C.AUDIO_CONTENT_TYPE_SPEECH]. The player was built with
 * music attributes and never changed them, so an episode was ducked like a song.
 *
 * Attributes are player-wide, so like [EpisodeSkipSilence] this is re-decided on every item change,
 * and only applied when they actually differ — setting them reconfigures the audio output.
 */
internal class EpisodeAudioAttributes(private val player: ExoPlayer) : Player.Listener {

    init {
        player.addListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val wanted = audioAttributesFor(isEpisode = mediaItem?.toUnifiedTrack()?.isEpisode == true)
        if (player.audioAttributes != wanted) {
            player.setAudioAttributes(wanted, /* handleAudioFocus = */ true)
        }
    }
}

/** The attributes the player plays under: speech for an episode, music for anything else. */
internal fun audioAttributesFor(isEpisode: Boolean): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (isEpisode) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
