package com.wander.android.core.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.wander.android.R
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The custom buttons in the media notification, lockscreen and car: the heart, and while an
 * episode plays, jumps back 10 s and forward 30 s in place of previous and next.
 *
 * Media3 draws these from the session's *media button preferences*, which is one list: whoever
 * publishes it publishes all of it, so both kinds of button live here. It is session state that has
 * to be republished whenever the answer changes — which is on a track change, and on a like landing
 * from anywhere (the notification itself, Now Playing, a track row).
 * [MusicRepository.getLikedTrackIdsFlow] is the one place that knows, so it is what this follows,
 * rather than a copy kept here that the rest of the app would have to remember to update.
 *
 * All icons are Media3's own constants, not drawables of ours: the shade, Android Auto and a watch
 * face each render a *known* icon in their own idiom, and a custom asset would be a Wanda-shaped
 * heart pasted into all three.
 *
 * The jumps are plain player commands, `seekBack` and `seekForward`, at the increments the player
 * is built with (see [PlayerFactory]), so they need no session command of their own.
 */
@UnstableApi
internal class NotificationButtons(
    private val context: Context,
    private val player: Player,
    private val musicRepository: MusicRepository,
    private val scope: CoroutineScope
) : Player.Listener {

    /** The command a controller sends when the heart is tapped. */
    val sessionCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)

    private var session: MediaSession? = null
    private var likedIds: Set<String> = emptySet()

    /**
     * Starts following the library, and publishes the first state.
     *
     * Called after the session is built rather than taking it in the constructor, because the
     * session's own builder needs [preferences] to seed the connection — the two are mutually
     * recursive by construction and this is the end that can wait.
     */
    fun attach(session: MediaSession) {
        this.session = session
        player.addListener(this)
        scope.launch {
            musicRepository.getLikedTrackIdsFlow()
                // Only the current track's membership can change this button, and the set changes
                // on every write to the library — a sync would otherwise republish the same icon
                // hundreds of times over IPC to say nothing.
                .map { ids -> likedIds = ids; isCurrentLiked() }
                .distinctUntilChanged()
                .collect { publish() }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        publish()
    }

    /** True when [command] is this button's, and it has been handled. */
    fun handle(command: SessionCommand): Boolean {
        if (command.customAction != ACTION_TOGGLE_LIKE) return false
        val track = player.currentMediaItem?.toUnifiedTrack() ?: return true
        // No optimistic repaint here: the write lands in Room, the liked-ids flow re-emits, and
        // `attach` publishes the new icon. One path to the icon means the notification cannot end
        // up showing a like the library did not keep.
        scope.launch { musicRepository.toggleLike(track) }
        return true
    }

    /** What the session should show right now. Also seeds a connecting controller. */
    fun preferences(): List<CommandButton> {
        val like = likeButton(isCurrentLiked())
        val isEpisode = player.currentMediaItem?.toUnifiedTrack()?.isEpisode == true
        return if (isEpisode) listOf(jumpBack(), jumpForward(), like) else listOf(like)
    }

    private fun isCurrentLiked(): Boolean {
        val id = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return false
        return id in likedIds
    }

    private fun publish() {
        session?.setMediaButtonPreferences(preferences())
    }

    private fun likeButton(liked: Boolean) = CommandButton.Builder(
        if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
    )
        .setSessionCommand(sessionCommand)
        .setDisplayName(context.getString(if (liked) R.string.action_unlike else R.string.menu_like))
        // A visible secondary slot first, the overflow menu only if the surface has no room for
        // one. Asking for the overflow alone would hide the button on the phone notification,
        // which is the one place it was added for.
        .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
        // Nothing playing means nothing to like — greyed rather than absent, so the row of
        // controls does not change width as tracks change.
        .setEnabled(player.currentMediaItem != null)
        .build()

    /** Takes the previous button's place: in an episode, back means a missed sentence. */
    private fun jumpBack() = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .setDisplayName(context.getString(R.string.player_rewind_10))
        .setSlots(CommandButton.SLOT_BACK)
        .build()

    /** Takes the next button's place: in an episode, forward means past a sponsor read. */
    private fun jumpForward() = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setDisplayName(context.getString(R.string.player_forward_30))
        .setSlots(CommandButton.SLOT_FORWARD)
        .build()

    private companion object {
        const val ACTION_TOGGLE_LIKE = "com.wander.android.action.TOGGLE_LIKE"
    }
}
