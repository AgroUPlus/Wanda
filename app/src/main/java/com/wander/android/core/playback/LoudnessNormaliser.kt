package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import com.wander.android.data.model.LoudnessMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

/**
 * Levels each track to ReplayGain 2.0's reference loudness, through [PlayerVolume].
 *
 * Re-decided whenever the playing item, its tracks or the queue change, and when a setting does.
 * The tags come from the selected audio format's metadata — parsed by Media3 during extraction —
 * so this covers anything played as a file with its tags intact: local files, downloads, and
 * Navidrome, whose streams are requested raw.
 *
 * While the new item's tracks are not known yet, the previous gain is kept rather than reset: a
 * reset would play the start of a loud track at full volume until its tags arrive.
 */
internal class LoudnessNormaliser(
    private val player: Player,
    private val volume: PlayerVolume,
    private val mode: StateFlow<LoudnessMode>,
    private val preampDb: StateFlow<Int>,
    private val readout: LoudnessReadout,
    scope: CoroutineScope
) : Player.Listener {

    init {
        player.addListener(this)
        combine(mode, preampDb) { _, _ -> apply() }.launchIn(scope)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
            )
        ) {
            apply()
        }
    }

    private fun apply() {
        val trackId = player.currentMediaItem?.mediaId
        val metadata = selectedAudioMetadata()
        if (trackId == null || metadata == null) return
        val decision = LoudnessGain.decide(
            info = ReplayGainTags.read(metadata.takeIf { it.length() > 0 }),
            mode = mode.value,
            preampDb = preampDb.value,
            inAlbumRun = inAlbumRun()
        )
        volume.setNormalisation(decision.factor)
        readout.publish(AppliedLoudness(trackId, decision))
    }

    /**
     * The metadata of the audio format actually playing, or null while it is not known. An empty
     * [Metadata] stands for "known, and untagged", which is a different answer from null.
     */
    private fun selectedAudioMetadata(): Metadata? {
        val group = player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        return (0 until group.length).firstOrNull(group::isTrackSelected)
            ?.let { group.getTrackFormat(it).metadata ?: Metadata() }
    }

    /** Whether the item before or after this one, in playing order, is from the same album. */
    private fun inAlbumRun(): Boolean {
        val albumId = player.currentMediaItem?.toUnifiedTrack()?.albumId ?: return false
        return listOf(player.previousMediaItemIndex, player.nextMediaItemIndex)
            .filter { it != C.INDEX_UNSET }
            .any { player.getMediaItemAt(it).toUnifiedTrack()?.albumId == albumId }
    }
}
