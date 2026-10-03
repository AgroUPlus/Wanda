package com.wander.android.ui.components

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.screens.social.JamViewModel

/**
 * What you can do with a track, on long press.
 *
 * Everything here was previously reachable only by playing the track first and then finding the
 * control in the player — or, for queueing, not at all. Actions the track's source cannot perform
 * are absent rather than present-and-disabled: a source never advertises a feature it lacks.
 *
 * There is no "play now": tapping the row already does exactly that, and the long press is for
 * everything tapping *cannot* do.
 *
 * The same absence rule covers offline: when the track cannot be played without a network, the
 * actions that would queue it are gone rather than present and doomed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: UnifiedTrack,
    isLiked: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onToggleLike: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Null unless the track's source can publish a public link — see `SourceCapabilities.share`. */
    onShare: (() -> Unit)? = null,
    /**
     * Null unless the track's source can be written to — see `SourceCapabilities.playlistWrite`.
     * Unlike the queue actions this survives offline: the write is worth attempting whether or not
     * the track itself would play right now.
     */
    onAddToPlaylist: (() -> Unit)? = null,
    /** Told what to say once a drop has been sent, or failed to be. */
    onDropSent: (String) -> Unit = {},
    /** Deletes an offline downloaded file from device storage. */
    onDeleteDownload: (() -> Unit)? = null,
    /**
     * Opens the artist's page. Null when the track names no artist — there is nowhere to go.
     *
     * Every screen that shows a track shows this sheet, so this is the way to an artist from
     * anywhere: a queue, a search result, a shelf on Home. Previously the only route in was
     * tapping the name on the full player, which meant the track had to be the one playing.
     */
    onOpenArtist: (() -> Unit)? = null,
    /** Forgets how far into an episode the listener got. Null for anything but a podcast episode. */
    onResetProgress: (() -> Unit)? = null,
    /** What [onRemove] is called, when it removes from something other than the queue. */
    removeLabel: String? = null,
    /** Reorders a playlist the caller may rearrange; null at either end, or when it may not. */
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val playable = track.isPlayableNow()
    // Held here and flipped optimistically: callers pass the track as it was when the menu opened,
    // so their `isLiked` would never reflect a toggle made from inside it.
    var liked by remember(track.id) { mutableStateOf(isLiked) }
    val likeLabel = stringResource(R.string.menu_like)
    val removeFromQueue = removeLabel ?: stringResource(R.string.action_remove_from_queue)
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val deleteOffline = stringResource(R.string.common_delete_offline_file)
    val resetProgress = stringResource(R.string.podcasts_reset_progress)

    // Resolved here rather than passed in by each caller. This sheet is opened from a dozen
    // screens, and threading a jam callback through all of them would mean the action quietly
    // missing wherever one was forgotten — which is how it shipped with no way to add a track at
    // all. Shown only while actually in a jam, since otherwise there is nowhere for it to go.
    val jamViewModel: JamViewModel = hiltViewModel()
    val jamState by jamViewModel.state.collectAsStateWithLifecycle()

    // Resolved here for the same reason the jam view model is: this sheet opens from a dozen
    // screens, and threading a callback through all of them is how an action ends up missing from
    // whichever one was forgotten.
    val dropFriends by hiltViewModel<DropToFriendViewModel>().friends.collectAsStateWithLifecycle()
    var pickingFriend by remember { mutableStateOf(false) }
    var choosingShare by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<ConfirmRequest?>(null) }

    // A destructive entry replaces the sheet with its confirmation, and either answer closes both.
    confirming?.let { request ->
        ConfirmDialog(request, onDismiss = {
            confirming = null
            onDismiss()
        })
        return
    }
    val deleteTitle = stringResource(R.string.confirm_delete_download_title)
    val deleteMessage = stringResource(R.string.confirm_delete_download_message, track.title)
    val resetTitle = stringResource(R.string.confirm_reset_progress_title)
    val resetMessage = stringResource(R.string.confirm_reset_progress_message, track.title)
    val askDeleteDownload = onDeleteDownload?.let { delete ->
        { confirming = ConfirmRequest(deleteTitle, deleteMessage, deleteOffline, delete) }
    }
    val askResetProgress = onResetProgress?.let { reset ->
        { confirming = ConfirmRequest(resetTitle, resetMessage, resetProgress, reset) }
    }

    if (choosingShare) {
        ShareChooserSheet(
            subject = track.title,
            onShareLink = {
                choosingShare = false
                onShare?.invoke()
                onDismiss()
            },
            onSendToFriend = {
                choosingShare = false
                pickingFriend = true
            }.takeIf { dropFriends.isNotEmpty() },
            onDismiss = {
                choosingShare = false
                onDismiss()
            }
        )
        return
    }

    if (pickingFriend) {
        DropToFriendSheet(
            track = track,
            onDismiss = {
                pickingFriend = false
                onDismiss()
            },
            onSent = onDropSent
        )
        return
    }

    WandaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) { animatedDismiss ->
        val pagerState = rememberPagerState(pageCount = { MenuSheetPageCount })

        // No overscroll on this outer scroll: it wraps `MenuSheetPager`'s `HorizontalPager`, and
        // the default stretch/glow overscroll effect fires on this vertical scroll's own edge
        // whenever that pager's height animates between pages — a bounce on the *wrong* axis, on
        // an ordinary page swipe rather than a real overscroll. See `LibraryAlbumGrid`'s own
        // `LocalOverscrollFactory` use for the same nested-scroll class of bug.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                TrackSheetHeader(track)

                val actions = buildTrackActionsList(
                    track = track,
                    state = TrackActionState(
                        playable = playable,
                        liked = liked,
                        hasDropFriends = dropFriends.isNotEmpty(),
                        jamState = jamState
                    ),
                    labels = TrackActionLabels(likeLabel, removeFromQueue, deleteOffline, resetProgress, moveUp, moveDown),
                    callbacks = TrackActionCallbacks(
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleLike = onToggleLike,
                        onShare = onShare,
                        onAddToPlaylist = onAddToPlaylist,
                        onStartRadio = onStartRadio,
                        onOpenArtist = onOpenArtist,
                        onRemove = onRemove,
                        onDeleteDownload = askDeleteDownload,
                        onResetProgress = askResetProgress,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown
                    ),
                    jamViewModel = jamViewModel,
                    sheet = TrackActionSheetFlow(
                        onToggleLikedState = { liked = !liked },
                        onPickFriend = { pickingFriend = true },
                        onChooseShare = { choosingShare = true },
                        animatedDismiss = animatedDismiss
                    )
                )

                MenuSheetPager(
                    state = pagerState,
                    modifier = Modifier.padding(top = 8.dp)
                ) { page ->
                    if (page == MenuSheetInfoPageIndex) {
                        TrackInfoPage(track)
                    } else {
                        ActionButtonGroup(actions)
                    }
                }

                MenuSheetPageIndicator(
                    pageCount = MenuSheetPageCount,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}
