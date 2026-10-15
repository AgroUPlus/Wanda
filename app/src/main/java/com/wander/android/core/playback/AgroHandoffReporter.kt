package com.wander.android.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wander.android.data.sources.agro.AgroHandoffPublisher

/**
 * Feeds the Agro handoff. It lives with the service rather than in `PlaybackCoordinator` so that
 * handoff follows playback even with no UI attached; [AgroHandoffPublisher] drops states that
 * repeat and no-ops entirely when no server is paired, so this costs nothing when unused.
 */
internal class AgroHandoffReporter(
    private val player: Player,
    private val publisher: AgroHandoffPublisher
) : Player.Listener {

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        report()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = report()

    private fun report() {
        val track = player.currentMediaItem?.toUnifiedTrack()
        if (track == null) {
            publisher.stop()
            return
        }
        // A provider, not a value: the publisher's heartbeat re-reads the position on each tick
        // rather than repeating a stale one.
        publisher.publish(
            track,
            player::getCurrentPosition,
            // The player's length, not the track metadata's: a YouTube Music row whose
            // subtitle carried no `3:45` reaches Room with 0, and the fleet would show a
            // progress bar that never fills. Read on the same thread and at the same moment
            // as the position, so the two describe one instant.
            player::getDuration,
            player.isPlaying
        )
    }
}
