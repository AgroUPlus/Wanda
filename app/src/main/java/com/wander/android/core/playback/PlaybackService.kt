package com.wander.android.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wander.android.MainActivity
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.sources.agro.AgroHandoffPublisher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the [ExoPlayer] and the [MediaSession]. The UI holds a `MediaController` onto this
 * service, which is what gives us the notification, lockscreen and Bluetooth controls, and lets
 * playback outlive the Activity.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playerFactory: PlayerFactory
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var agroHandoffPublisher: AgroHandoffPublisher
    @Inject lateinit var secureStorage: com.wander.android.core.security.SecureStorage

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerFactory.create()
        player.addListener(PlayCountRecorder(player))
        player.addListener(AgroHandoffReporter(player))
        player.addListener(NextTrackPreloader(player))

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    /**
     * Fetches the start of the next track, when the next track is one it is safe to fetch.
     *
     * Media3's preload configuration is a property of the whole player, not of an item, so it is
     * re-decided every time the queue moves — which is to say it is armed the moment a song starts,
     * and the next track is fetched alongside it rather than when the skip arrives. See
     * [PRELOAD_TARGET_US] for how much runway that buys.
     *
     * The refusals matter more than the feature. See [PreloadDecision] — a relay session serves its
     * receiving half exactly once, so preloading one would consume the transfer and leave the
     * actual playback with a `409`.
     */
    private inner class NextTrackPreloader(private val player: ExoPlayer) : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = apply()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = apply()

        private fun apply() {
            val next = runCatching {
                player.nextMediaItemIndex
                    .takeIf { it != C.INDEX_UNSET }
                    ?.let(player::getMediaItemAt)
            }.getOrNull()

            val allowed = secureStorage.isPreloadNextEnabled.value &&
                PreloadDecision.canPreload(next?.mediaId, isLive = next?.isLiveUri() == true)
            player.preloadConfiguration = if (allowed) {
                ExoPlayer.PreloadConfiguration(PRELOAD_TARGET_US)
            } else {
                ExoPlayer.PreloadConfiguration.DEFAULT
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Nothing left playing and the user swiped the task away: don't linger as a foreground service. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        agroHandoffPublisher.stop()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    /** Records a play once a track reaches 75% of its duration, so counts reflect actual listening. */
    private inner class PlayCountRecorder(private val player: Player) : Player.Listener {
        private var recordedFor: String? = null
        private var scrobbleJob: Job? = null

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            recordedFor = null
            scrobbleJob?.cancel()
            scrobbleJob = null
            checkScrobbleProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                checkScrobbleProgress()
            } else {
                scrobbleJob?.cancel()
                scrobbleJob = null
            }
        }

        private fun checkScrobbleProgress() {
            scrobbleJob?.cancel()
            if (!player.isPlaying) return
            val track = player.currentMediaItem?.toUnifiedTrack() ?: return
            if (recordedFor == track.id) return

            scrobbleJob = scope.launch {
                while (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = if (track.durationMs > 0) track.durationMs else player.duration

                    // 75% threshold, with a 15s minimum for short tracks, capped at 4 min for long audio
                    val thresholdMs = if (duration > 0) {
                        minOf((duration * 0.75).toLong(), 240_000L).coerceAtLeast(15_000L)
                    } else {
                        30_000L
                    }

                    if (currentPos >= thresholdMs) {
                        if (recordedFor != track.id) {
                            recordedFor = track.id
                            musicRepository.recordPlay(track)
                        }
                        break
                    }
                    delay(2_000L)
                }
            }
        }
    }

    /**
     * Feeds the Agro handoff. It lives here rather than in `PlaybackCoordinator` so that handoff
     * follows playback even with no UI attached; [AgroHandoffPublisher] drops states that repeat
     * and no-ops entirely when no server is paired, so this costs nothing when unused.
     */
    private inner class AgroHandoffReporter(private val player: Player) : Player.Listener {

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            report()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = report()

        private fun report() {
            val track = player.currentMediaItem?.toUnifiedTrack()
            if (track == null) {
                agroHandoffPublisher.stop()
                return
            }
            // A provider, not a value: the publisher's heartbeat re-reads the position on each tick
            // rather than repeating a stale one.
            agroHandoffPublisher.publish(
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

    private companion object {
        /**
         * How much of the next track to have ready.
         *
         * Two seconds was sized for the gap in the abstract — the round trip to resolve a URL and
         * fill the first buffer — and it is not what a skip actually feels like. Two seconds of
         * audio is consumed in two seconds, so the player starts instantly and then stalls waiting
         * for the rest, which reads as exactly the loading pause the preload was meant to remove.
         *
         * Fifteen seconds is enough runway for the loader to stay ahead on any connection worth
         * streaming over, and it is still a small fraction of a track — the bound on wasted data
         * is fifteen seconds per skip, not a whole song. Preloading remains off for anyone
         * counting megabytes; that is what the switch is for.
         */
        const val PRELOAD_TARGET_US = 15_000_000L
    }

}
