package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wander.android.data.model.UnifiedTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds [PlaybackState] snapshots from a [Player] instance and a track cache.
 */
internal object PlaybackSnapshotBuilder {

    fun buildSnapshot(
        player: Player,
        radio: Boolean,
        cachedQueue: List<UnifiedTrack>,
        cache: ConcurrentHashMap<String, UnifiedTrack>,
        seekEpoch: Long
    ): PlaybackState {
        val activeItem = runCatching { player.currentMediaItem }.getOrNull()
        val track = activeItem?.resolveTrack(cache)
        val playing = runCatching { player.isPlaying }.getOrDefault(false)
        val buffering = runCatching { player.playbackState == Player.STATE_BUFFERING }.getOrDefault(false)

        val reported = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
        val dur = if (reported == C.TIME_UNSET || reported <= 0L) {
            track?.durationMs ?: 0L
        } else {
            reported
        }

        val seekable = runCatching {
            if (track?.isLive == true) {
                false
            } else {
                player.currentTimeline.isEmpty || player.isCurrentMediaItemSeekable
            }
        }.getOrDefault(true)

        val curIndex = runCatching { player.currentMediaItemIndex }.getOrDefault(0)
        val shuffle = runCatching { player.shuffleModeEnabled }.getOrDefault(false)
        val repMode = runCatching { player.repeatMode }.getOrDefault(Player.REPEAT_MODE_OFF)

        val audioTracks = runCatching {
            val seen = mutableSetOf<String?>()
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    (0 until group.length).mapNotNull { i ->
                        val fmt = group.getTrackFormat(i)
                        if (!seen.add(fmt.language)) null
                        else AudioTrackInfo(
                            language = fmt.language,
                            label = fmt.label,
                            isSelected = group.isTrackSelected(i)
                        )
                    }
                }
        }.getOrDefault(emptyList())

        return PlaybackState(
            currentTrack = track,
            queue = cachedQueue,
            currentIndex = curIndex,
            isPlaying = playing,
            isBuffering = buffering,
            durationMs = dur,
            isSeekable = seekable,
            isShuffle = shuffle,
            repeatMode = when (repMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            isRadioMode = radio,
            seekEpoch = seekEpoch,
            audioTracks = audioTracks
        )
    }

    fun queueTracks(
        player: Player,
        cache: ConcurrentHashMap<String, UnifiedTrack>
    ): List<UnifiedTrack> =
        (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).resolveTrack(cache) }

    fun MediaItem.resolveTrack(cache: ConcurrentHashMap<String, UnifiedTrack>): UnifiedTrack? {
        cache[mediaId]?.let { return it }
        val track = toUnifiedTrack() ?: return null
        cache[mediaId] = track
        return track
    }
}
