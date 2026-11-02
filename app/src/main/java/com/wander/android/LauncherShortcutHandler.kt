package com.wander.android

import android.content.Intent
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.LaunchNotices
import com.wander.android.ui.navigation.ShellRequest
import com.wander.android.ui.navigation.ShellRequests
import com.wander.android.ui.navigation.TopLevelDestination
import com.wander.android.ui.screens.library.LibraryBrowsePrefs
import com.wander.android.ui.screens.library.LibraryTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's long-press shortcuts (`res/xml/shortcuts.xml`): `wanda://shortcut/<action>`.
 *
 * Every one of them goes through `MainActivity` rather than straight to the screen it is about —
 * including Recognize, which opens the shell's own listen sheet rather than `RecognitionActivity`,
 * so the microphone permission is asked for the same way the dock's own button asks.
 */
@Singleton
internal class LauncherShortcutHandler @Inject constructor(
    private val deepLinkRouter: DeepLinkRouter,
    private val shellRequests: ShellRequests,
    private val libraryBrowse: LibraryBrowseRepository,
    private val browsePrefs: LibraryBrowsePrefs,
    private val playerConnection: PlayerConnection,
    private val notices: LaunchNotices
) {

    /**
     * Whether [intent] is a launcher shortcut, carried out here and not to be passed on.
     *
     * [firstDelivery] is false when the Activity is recreated with the intent it already acted on;
     * the shortcut is still claimed, but a rotation must not shuffle the queue a second time.
     */
    fun handle(scope: CoroutineScope, intent: Intent?, firstDelivery: Boolean): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "wanda" || uri.host != HOST) return false
        val action = uri.lastPathSegment ?: return false
        if (action !in ACTIONS) return false
        if (!firstDelivery) return true
        when (action) {
            SEARCH -> {
                deepLinkRouter.request(TopLevelDestination.LIBRARY.route)
                shellRequests.request(ShellRequest.FOCUS_SEARCH)
            }
            SHUFFLE_LIKED -> scope.launch { shuffleLiked() }
            DOWNLOADS -> {
                // Read by the Library as the section to open when it has no saved one of its own,
                // which a shortcut's fresh task never does.
                browsePrefs.setLastTab(LibraryTab.DOWNLOADS)
                deepLinkRouter.request(TopLevelDestination.LIBRARY.route)
            }
            RECOGNIZE -> shellRequests.request(ShellRequest.LISTEN)
        }
        return true
    }

    /** Every liked song from every source, in a fresh random order — what the Liked tab's shuffle plays. */
    private suspend fun shuffleLiked() {
        val liked = libraryBrowse.likedTracks(LibrarySort.TITLE, emptySet()).first()
        if (liked.isEmpty()) {
            notices.post(R.string.shortcut_no_liked_songs)
        } else {
            playerConnection.play(liked.shuffled(), 0)
        }
    }

    private companion object {
        const val HOST = "shortcut"
        const val SEARCH = "search"
        const val SHUFFLE_LIKED = "shuffle-liked"
        const val DOWNLOADS = "downloads"
        const val RECOGNIZE = "recognize"
        val ACTIONS = setOf(SEARCH, SHUFFLE_LIKED, DOWNLOADS, RECOGNIZE)
    }
}
