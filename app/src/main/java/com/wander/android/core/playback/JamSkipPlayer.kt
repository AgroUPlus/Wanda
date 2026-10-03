package com.wander.android.core.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The player the media session sees: the real one, except that "next" is a vote while in a jam.
 *
 * Wrapping the session's player is what reaches the controls the app does not draw — the
 * notification, the lockscreen, a headset's double-press. In a jam the local queue holds only the
 * room's current track, so without this "next" was either missing from those surfaces or a no-op.
 * Offered whenever the gate is active, so the button is there to press.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class JamSkipPlayer(
    player: Player,
    private val gate: JamSkipGate
) : ForwardingPlayer(player) {

    /** The session's listeners, kept so a change in [gate] can be announced to them. */
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!gate.active.value) return base
        return base.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        getAvailableCommands().contains(command)

    override fun seekToNext() {
        if (!gate.tryVote()) super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (!gate.tryVote()) super.seekToNextMediaItem()
    }

    /** Re-announces the commands after the gate flips. Call on the player's application thread. */
    fun onGateChanged() {
        val commands = availableCommands
        listeners.forEach { it.onAvailableCommandsChanged(commands) }
    }
}
