package com.wander.android.core.playback

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.UnifiedTrack

enum class RepeatMode { OFF, ALL, ONE }

/**
 * One language track available in the current media item.
 *
 * [language] is the BCP-47 tag (e.g. `"en"`, `"fr"`, `"ja"`) or null when the stream has no
 * language metadata. [label] is the human-readable name the stream carries, if any. The UI falls
 * back to the language tag — or "Unknown" — when the label is absent.
 *
 * [isSelected] mirrors what ExoPlayer has actually chosen, so the picker can mark the active row
 * without any additional state.
 */
@Immutable
data class AudioTrackInfo(
    val language: String?,
    val label: String?,
    val isSelected: Boolean
)

/** Everything the UI needs about playback, in one snapshot. Position is deliberately separate. */
@Immutable
data class PlaybackState(
    val currentTrack: UnifiedTrack? = null,
    val queue: List<UnifiedTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isRadioMode: Boolean = false,
    /**
     * True when something else decides the running order — a jam, or a listen-along.
     *
     * Shuffle and repeat are inert while it is set, and the controls read it to say so rather than
     * accepting a tap and doing nothing. Skip and seek are *not* covered: a jam tolerates a local
     * seek and corrects it, which is a different rule from owning the order outright.
     */
    val orderLocked: Boolean = false,
    /**
     * Bumped every time the position jumps rather than advances — a seek, a track boundary.
     *
     * Position is deliberately not in this snapshot (see the note above), and while paused it is
     * the *only* thing a seek changes: same track, same paused, same duration. So the snapshot was
     * identical to the previous one, `distinctUntilChanged` dropped it, and nothing downstream
     * ever learned the playhead had moved. Tapping a lyric while paused jumped the audio and left
     * the highlight where it was.
     *
     * A counter rather than the position itself, because a position in here would recompose every
     * reader twice a second — which is the thing keeping it out was for.
     */
    val seekEpoch: Long = 0L,
    /**
     * Audio tracks available in the current media item, deduplicated by language.
     *
     * Empty for ordinary music (single track). Populated for podcasts and videos that carry
     * more than one language — those are the only cases where the picker makes sense, and the
     * UI gates the button on `audioTracks.size > 1` rather than `isNotEmpty()` to avoid showing
     * a single-option menu.
     */
    val audioTracks: List<AudioTrackInfo> = emptyList()
) {
    /**
     * The same snapshot with [liveIds] known to be livestreams marked as such.
     *
     * A track's own `isLive` is whatever the source claimed when it was queued — for YouTube, a
     * badge hunt over a response whose shape changes; for a link-opened track, usually nothing at
     * all. [liveIds] is what a stream *resolve* proved, which is the only moment anything knows
     * for certain. See `StreamResolver.resolvedLive`.
     *
     * One-way: it can only mark a track live, never un-mark one. A resolve that came back
     * progressive says this play is progressive, not that the recording is never a broadcast, and
     * the item that is already flying as HLS must not have the ground taken out from under it.
     */
    internal fun withLiveIds(liveIds: Set<String>): PlaybackState {
        if (liveIds.isEmpty()) return this
        val track = currentTrack?.let { if (it.id in liveIds && !it.isLive) it.copy(isLive = true) else it }
        val patchedQueue = if (queue.any { it.id in liveIds && !it.isLive }) {
            queue.map { if (it.id in liveIds && !it.isLive) it.copy(isLive = true) else it }
        } else {
            queue
        }
        return if (track === currentTrack && patchedQueue === queue) {
            this
        } else {
            copy(currentTrack = track, queue = patchedQueue)
        }
    }
}

/**
 * Playback rate and pitch.
 *
 * One value, not two, because Media3 sets them together — see
 * [PlayerConnection.setSpeedAndPitch]. Pitch is independent of speed here: slowing a track down
 * without dropping its key is the point of having both.
 */
@Immutable
data class SpeedAndPitch(
    val speed: Float = 1f,
    val pitch: Float = 1f
) {
    val isDefault: Boolean get() = speed == 1f && pitch == 1f

    companion object {
        /**
         * What the player will accept, and therefore what a share link may ask for.
         *
         * Lives here rather than beside the sliders because it is a fact about playback, not about
         * the popup: a link arriving from someone else's phone is checked against the same bounds
         * the local UI offers, in one place, so the two cannot drift apart.
         */
        val RANGE = 0.5f..2.0f
    }
}

@Immutable
data class PlaybackPosition(
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L
)
