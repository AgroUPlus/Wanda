package com.wander.android.core.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.common.C
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the player's audio session to whatever equalizer is installed — the system's, Samsung's,
 * Wavelet — the way the platform defines it: an open broadcast when a session starts, a close when
 * it ends. Without the broadcast those apps never learn the session exists and have nothing to
 * attach their effect to.
 *
 * The service drives it as a [Player.Listener], since the session id arrives after the player is
 * built and can change; the now-playing menu reads [controlPanelIntent] for its "Equalizer" entry.
 * Both run in the app's one process, so the current id is simply held here.
 *
 * Offload is not told about it. Whether an external effect is actually attached is not something
 * the platform reports to the app, so there is nothing cheap to feed into [offloadAllowed]; an
 * effect that cannot run offloaded is the platform's to reconcile with an offloaded track.
 */
@Singleton
class ExternalEqualizer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : Player.Listener {

    @Volatile
    private var sessionId = C.AUDIO_SESSION_ID_UNSET

    override fun onAudioSessionIdChanged(audioSessionId: Int) = open(audioSessionId)

    /** Announces [audioSessionId], closing whichever session was announced before it. */
    fun open(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == sessionId) return
        close()
        sessionId = audioSessionId
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId))
    }

    /** Tells equalizers the session is gone. Call when the player is released. */
    fun close() {
        val id = sessionId
        if (id == C.AUDIO_SESSION_ID_UNSET) return
        sessionId = C.AUDIO_SESSION_ID_UNSET
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, id))
    }

    /**
     * The installed equalizer's panel for the current session, or null when there is no session
     * yet or nothing on the device handles it — in which case the menu shows no entry at all.
     * The panel must be started for a result, as the platform documents.
     */
    fun controlPanelIntent(): Intent? {
        val id = sessionId
        if (id == C.AUDIO_SESSION_ID_UNSET) return null
        return sessionIntent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL, id)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun sessionIntent(action: String, id: Int) = Intent(action)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
}
