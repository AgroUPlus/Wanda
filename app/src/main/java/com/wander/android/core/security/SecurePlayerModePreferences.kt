package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wander.android.core.playback.SpeedAndPitch

/**
 * How the player was last set up by the listener: shuffle, repeat, and speed and pitch.
 *
 * Speed and pitch are kept twice, once for podcast episodes and once for everything else. A
 * listener who takes episodes at 1.5x does not want the next song at 1.5x, and switching back by
 * hand at every change of kind is exactly what remembering it was meant to remove.
 *
 * Read straight from storage rather than mirrored into flows: each value is read once, when a
 * player is set up or the playing kind changes, and nothing observes it.
 */
internal class SecurePlayerModePreferences(private val prefs: SharedPreferences) {

    val shuffle: Boolean get() = prefs.getBoolean(KEY_SHUFFLE, false)

    fun setShuffle(enabled: Boolean) = prefs.edit { putBoolean(KEY_SHUFFLE, enabled) }

    /** A `Player.REPEAT_MODE_*` value: 0 off, 1 one, 2 all. */
    val repeatMode: Int get() = prefs.getInt(KEY_REPEAT_MODE, 0).takeIf { it in 0..2 } ?: 0

    fun setRepeatMode(mode: Int) = prefs.edit { putInt(KEY_REPEAT_MODE, mode) }

    fun speedAndPitch(forEpisodes: Boolean): SpeedAndPitch = SpeedAndPitch(
        speed = prefs.getFloat(if (forEpisodes) KEY_EPISODE_SPEED else KEY_MUSIC_SPEED, 1f)
            .coerceIn(SpeedAndPitch.RANGE),
        pitch = prefs.getFloat(if (forEpisodes) KEY_EPISODE_PITCH else KEY_MUSIC_PITCH, 1f)
            .coerceIn(SpeedAndPitch.RANGE)
    )

    fun setSpeedAndPitch(forEpisodes: Boolean, value: SpeedAndPitch) = prefs.edit {
        putFloat(if (forEpisodes) KEY_EPISODE_SPEED else KEY_MUSIC_SPEED, value.speed)
        putFloat(if (forEpisodes) KEY_EPISODE_PITCH else KEY_MUSIC_PITCH, value.pitch)
    }
}
