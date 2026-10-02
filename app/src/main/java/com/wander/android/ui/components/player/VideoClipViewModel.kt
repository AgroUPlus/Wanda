package com.wander.android.ui.components.player

import android.content.Context
import androidx.lifecycle.ViewModel
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.sources.StreamInfo
import com.wander.android.ui.screens.player.MediaFormController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * A googlevideo URL carries its own expiry, hours out; reusing one for this long means reopening
 * the player does not cost a fresh `/player` call, without getting anywhere near that limit.
 */
private const val STREAM_REUSE_MS = 30 * 60 * 1_000L

/**
 * Finds the clip stream for [VideoClip] and says so when there is none.
 *
 * The clip is found again on every open of the player — the composable that plays it does not
 * outlive a collapse — so the last stream per track is kept for a while rather than re-requested.
 */
@HiltViewModel
internal class VideoClipViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection,
    mediaForms: MediaFormController,
    val okHttpClient: OkHttpClient
) : ViewModel() {

    /** The playing track while it plays as a video — the one whose clip replaces the cover. */
    val clipTrack = mediaForms.clipTrack

    /** The audio the clip follows. */
    val audio = playerConnection.controller

    private class Resolved(val trackId: String, val stream: StreamInfo, val at: Long)

    private var last: Resolved? = null

    /** Tracks whose failure has been reported, so reopening the player does not repeat it. */
    private val reported = HashSet<String>()

    /** The clip's stream, or null when it cannot be had — the cover then stays, and the user is told. */
    suspend fun stream(track: UnifiedTrack): StreamInfo? {
        last?.takeIf { it.trackId == track.id && System.currentTimeMillis() - it.at < STREAM_REUSE_MS }
            ?.let { return it.stream }
        return musicRepository.getVideoStreamInfo(track).fold(
            onSuccess = { stream ->
                last = Resolved(track.id, stream, System.currentTimeMillis())
                stream
            },
            onFailure = {
                clipFailed(track)
                null
            }
        )
    }

    /** The stream resolved but would not play: drop it so the next open asks again, and say so. */
    fun clipFailed(track: UnifiedTrack) {
        if (last?.trackId == track.id) last = null
        if (reported.add(track.id)) playerConnection.notify(context.getString(R.string.video_clip_unavailable))
    }
}
