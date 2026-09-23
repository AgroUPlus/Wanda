package com.wander.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onOpenArtist: (() -> Unit)? = null
) {
    val playable = track.isPlayableNow()
    // Held here and flipped optimistically: callers pass the track as it was when the menu opened,
    // so their `isLiked` would never reflect a toggle made from inside it.
    var liked by remember(track.id) { mutableStateOf(isLiked) }
    val likeLabel = stringResource(R.string.menu_like)
    val removeFromQueue = stringResource(R.string.action_remove_from_queue)
    val deleteOffline = stringResource(R.string.common_delete_offline_file)

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            TrackSheetHeader(track)

            // Queueing a track the player would refuse to load only moves the failure later, so
            // offline these are omitted the same way an unsupported capability is. Liking,
            // sharing and removing still work — none of them need to play anything.
            val actions = buildList {
                if (playable) {
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next", ActionEmphasis.PRIMARY) { onPlayNext(); onDismiss() })
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", ActionEmphasis.SECONDARY) { onAddToQueue(); onDismiss() })
                }
                onToggleLike?.let {
                    // A setting, not an errand: it flips in place and the menu stays up.
                    add(
                        MenuAction(
                            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            label = likeLabel,
                            emphasis = ActionEmphasis.ICON,
                            selected = liked
                        ) {
                            liked = !liked
                            it()
                        }
                    )
                }
                // One verb, then a question — rather than two buttons that ask the user to know
                // Wanda's internal distinction between a public URL and a drop before they have
                // decided who they are sharing with. Shown when either half is available.
                if (onShare != null || dropFriends.isNotEmpty()) {
                    add(MenuAction(Icons.Rounded.Share, "Share", ActionEmphasis.ICON) {
                        if (onShare == null) pickingFriend = true else choosingShare = true
                    })
                }
                onAddToPlaylist?.let {
                    add(MenuAction(Icons.Rounded.LibraryAdd, "Add to playlist", ActionEmphasis.ICON) { it(); onDismiss() })
                }
                if (playable) {
                    onStartRadio?.let { add(MenuAction(Icons.Rounded.Radio, "Radio") { it(); onDismiss() }) }
                    onOpenArtist?.let { add(MenuAction(Icons.Rounded.Person, "Artist") { it(); onDismiss() }) }
                }
                jamState.jam?.let { jam ->
                    // Named for what it does. In democracy mode this does not add anything — it
                    // asks the room, and saying "add" would promise something the server won't do.
                    val label = if (jam.mode == com.wander.android.data.sources.agro.JamMode.DEMOCRACY) {
                        "Suggest"
                    } else {
                        "Jam"
                    }
                    add(MenuAction(Icons.Rounded.Groups, label) { jamViewModel.suggest(track); onDismiss() })
                }
                onRemove?.let {
                    add(MenuAction(Icons.Rounded.Delete, removeFromQueue, ActionEmphasis.DANGER) { it(); onDismiss() })
                }
                onDeleteDownload?.let {
                    add(MenuAction(Icons.Rounded.Delete, deleteOffline, ActionEmphasis.DANGER) { it(); onDismiss() })
                }
            }
            ActionButtonGroup(actions, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
