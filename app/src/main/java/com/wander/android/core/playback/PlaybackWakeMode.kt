package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.isOneShotTrackId

/**
 * Holds the Wi-Fi lock only while something has to come over the network.
 *
 * `WAKE_MODE_NETWORK` keeps a CPU wake lock *and* a Wi-Fi lock for as long as playback runs; a
 * file on the device needs only the CPU. So the mode is `WAKE_MODE_LOCAL` while both the playing
 * item and the one after it are on the device — a local file, or a download the queue knew about —
 * and `WAKE_MODE_NETWORK` otherwise. The next item counts because it is buffered before it plays:
 * releasing the Wi-Fi lock under the last local track would starve the stream queued after it.
 *
 * Peer and relay tracks are typed `LOCAL` but arrive over the network, so they count as remote. A
 * track downloaded after it was queued still reads as remote here, which only costs the lock.
 */
internal class PlaybackWakeMode(private val player: ExoPlayer) : Player.Listener {

    private var applied: Int? = null

    init {
        player.addListener(this)
        apply()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED
            )
        ) {
            apply()
        }
    }

    private fun apply() {
        val current = player.currentMediaItem
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let(player::getMediaItemAt)
        val mode = if (current != null && current.isOnDevice() && (next == null || next.isOnDevice())) {
            C.WAKE_MODE_LOCAL
        } else {
            C.WAKE_MODE_NETWORK
        }
        if (mode != applied) {
            player.setWakeMode(mode)
            applied = mode
        }
    }

    private fun MediaItem.isOnDevice(): Boolean {
        val track = toUnifiedTrack() ?: return false
        if (isOneShotTrackId(track.id)) return false
        return track.isDownloaded || track.source == SourceType.LOCAL
    }
}
