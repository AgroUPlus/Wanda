package com.wander.android.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The heart in the media notification: like what is playing without opening the app.
 *
 * Media3 draws this from the session's *media button preferences*, so the icon is a piece of
 * session state that has to be republished whenever the answer changes — which is on a track
 * change, and on a like landing from anywhere (the notification itself, Now Playing, a track row).
 * [MusicRepository.getLikedTrackIdsFlow] is the one place that knows, so it is what this follows,
 * rather than a copy kept here that the rest of the app would have to remember to update.
 *
 * `ICON_HEART_FILLED` and `ICON_HEART_UNFILLED` are Media3's own icon constants, not drawables of
 * ours: the shade, Android Auto and a watch face each render a *known* icon in their own idiom,
 * and a custom asset would be a Wanda-shaped heart pasted into all three.
 *
 * Its own file rather than another inner class of [PlaybackService], which is already at the length
 * where the next thing added to it stops being findable.
 */
@UnstableApi
internal class NotificationLikeButton(
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
                .collect { liked -> publish(liked) }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        publish(isCurrentLiked())
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
    fun preferences(): List<CommandButton> = listOf(button(isCurrentLiked()))

    private fun isCurrentLiked(): Boolean {
        val id = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return false
        return id in likedIds
    }

    private fun publish(liked: Boolean) {
        session?.setMediaButtonPreferences(listOf(button(liked)))
    }

    private fun button(liked: Boolean) = CommandButton.Builder(
        if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
    )
        .setSessionCommand(sessionCommand)
        .setDisplayName(if (liked) "Remove from liked" else "Like")
        // A visible secondary slot first, the overflow menu only if the surface has no room for
        // one. Asking for the overflow alone would hide the button on the phone notification,
        // which is the one place it was added for.
        .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
        // Nothing playing means nothing to like — greyed rather than absent, so the row of
        // controls does not change width as tracks change.
        .setEnabled(player.currentMediaItem != null)
        .build()

    private companion object {
        const val ACTION_TOGGLE_LIKE = "com.wander.android.action.TOGGLE_LIKE"
    }
}
