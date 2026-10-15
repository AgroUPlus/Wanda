package com.wander.android.core.playback

/**
 * The one writer of the player's volume.
 *
 * Two things set it: loudness normalisation, per track, and the sleep timer's fade. Each used to be
 * free to write `player.volume` directly, and the fade saved the volume it found and put it back
 * when it ended — so a track change during a fade restored the previous track's gain. Here each
 * owns its own factor and the volume is always their product, whichever changes and in whatever
 * order.
 *
 * Main thread only, like the player it writes to.
 */
internal class PlayerVolume(private val write: (Float) -> Unit) {

    private var normalisation = 1f
    private var fade = 1f

    fun setNormalisation(factor: Float) {
        normalisation = factor
        write(normalisation * fade)
    }

    fun setFade(factor: Float) {
        fade = factor
        write(normalisation * fade)
    }
}
