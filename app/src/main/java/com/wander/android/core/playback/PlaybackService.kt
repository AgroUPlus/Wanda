package com.wander.android.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wander.android.MainActivity
import com.wander.android.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerFactory.create()
        player.addListener(PlayCountRecorder(player))

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Nothing left playing and the user swiped the task away: don't linger as a foreground service. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    /** Records a play once a track actually starts, so counts reflect listening, not queuing. */
    private inner class PlayCountRecorder(private val player: Player) : Player.Listener {
        private var recordedFor: String? = null

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            recordedFor = null
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            val track = player.currentMediaItem?.toUnifiedTrack() ?: return
            if (recordedFor == track.id) return
            recordedFor = track.id
            scope.launch { musicRepository.recordPlay(track) }
        }
    }
}
