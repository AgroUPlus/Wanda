package com.wander.android.core.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wander.android.core.playback.browse.BrowseTree
import com.wander.android.core.playback.browse.LibrarySessionCallback
import com.wander.android.core.playback.browse.PlayRequestResolver
import kotlinx.coroutines.CoroutineScope

/**
 * The [PlaybackService] session's callback: which commands a controller gets, the notification's
 * custom buttons, and playback resumption. Browsing, search and other apps' play requests are
 * [LibrarySessionCallback]'s.
 *
 * [ownPackage] is this app's package. Controllers from it — the app, the widget, the media
 * notification — open on the saved queue as they connect; see [QueueResumption.restoreIfEmpty].
 */
@UnstableApi
internal class PlaybackSessionCallback(
    private val ownPackage: String,
    private val buttons: NotificationButtons,
    private val resumption: QueueResumption,
    tree: BrowseTree,
    requests: PlayRequestResolver,
    scope: CoroutineScope
) : LibrarySessionCallback(ownPackage, tree, requests, scope) {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            // The defaults plus the heart. Without adding it here the button is drawn but every
            // tap on it is rejected as an unavailable command. The library commands are in the
            // defaults' library set; BrowserAccess decides who is answered.
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(buttons.sessionCommand)
                    .build()
            )
            // Seeds the controller as it connects; `buttons` republishes from then on.
            .setMediaButtonPreferences(buttons.preferences())
            .build()

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (controller.packageName == ownPackage) resumption.restoreIfEmpty()
    }

    /**
     * What a play request on an empty player resumes: a Bluetooth play button, or a widget tap,
     * after the process was killed. Media3 sets the items and starts playback itself.
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = resumption.resumption(isForPlayback)

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> =
        if (buttons.handle(customCommand)) {
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else {
            super.onCustomCommand(session, controller, customCommand, args)
        }
}
