package com.wander.android.core.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope

/**
 * The half of the playback session's callback that serves other apps: the browse tree a car
 * walks, its search, and the items another app asks to play.
 * [com.wander.android.core.playback.PlaybackSessionCallback] adds the app's own half on top.
 *
 * Requests run in [scope], the service's main-thread scope, because Media3 expects every answer
 * on the session's thread; the Room reads behind them suspend rather than block it.
 */
@UnstableApi
internal open class LibrarySessionCallback(
    ownPackage: String,
    private val tree: BrowseTree,
    private val requests: PlayRequestResolver,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {

    private val access = BrowserAccess(ownPackage)

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> =
        if (requests.isReady(mediaItems)) {
            Futures.immediateFuture(mediaItems)
        } else {
            scope.sessionFuture { requests.resolveEach(mediaItems).toMutableList() }
        }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        if (requests.isReady(mediaItems)) {
            // The app's own queue, untouched: the same items Media3 would have set without us.
            Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        } else {
            scope.sessionFuture { requests.resolveQueue(mediaItems, startIndex, startPositionMs) }
        }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        if (access.mayBrowse(browser)) LibraryResult.ofItem(tree.root, rootParams()) else denied()
    )

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = answer(browser) {
        val item = BrowseId.decode(mediaId)?.let { tree.item(it) }
        if (item != null) LibraryResult.ofItem(item, null) else badValue()
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = answer(browser) {
        val node = BrowseId.decode(parentId) as? BrowseId.Node
        val children = node?.let { tree.children(it, page, pageSize) }
        if (children != null) LibraryResult.ofItemList(children, params) else badValue()
    }

    /** Library search only: a car searches while it may be offline, and Room answers offline. */
    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = answer(browser) {
        session.notifySearchResultChanged(browser, query, tree.searchCount(query), params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = answer(browser) {
        val results = tree.searchResults(query, page, pageSize)
        if (results != null) LibraryResult.ofItemList(results, params) else badValue()
    }

    private fun <T : Any> answer(
        browser: MediaSession.ControllerInfo,
        block: suspend () -> LibraryResult<T>
    ): ListenableFuture<LibraryResult<T>> =
        if (access.mayBrowse(browser)) scope.sessionFuture { block() } else Futures.immediateFuture(denied())
}

private fun <T : Any> denied(): LibraryResult<T> = LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)

/** An id this tree never handed out, one that no longer exists, or a page that cannot. */
private fun <T : Any> badValue(): LibraryResult<T> = LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)

/** Tells a car it may offer its search box and voice search for this app. */
private fun rootParams(): LibraryParams = LibraryParams.Builder()
    .setExtras(Bundle().apply { putBoolean(EXTRA_SEARCH_SUPPORTED, true) })
    .build()

/** `androidx.media`'s `BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED`. */
private const val EXTRA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
