package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onDropSent: (String) -> Unit = {}
) {
    val playable = track.isPlayableNow()

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

    ModalBottomSheet(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Artwork(
                    url = track.artworkUrl,
                    contentDescription = null,
                    sizeDp = 48.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Queueing a track the player would refuse to load only moves the failure later, so
            // offline these are omitted the same way an unsupported capability is. Liking,
            // sharing and removing still work — none of them need to play anything.
            if (playable) {
                SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next") { onPlayNext(); onDismiss() }
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { onAddToQueue(); onDismiss() }

                onStartRadio?.let {
                    SheetAction(Icons.Rounded.Radio, "Start radio from this") { it(); onDismiss() }
                }
            }
            jamState.jam?.let { jam ->
                // Named for what it does. In democracy mode this does not add anything — it asks
                // the room, and saying "add" would promise something the server will not do.
                val label = if (jam.mode == com.wander.android.data.sources.agro.JamMode.DEMOCRACY) {
                    "Suggest to jam"
                } else {
                    "Add to jam"
                }
                SheetAction(Icons.Rounded.Groups, label) {
                    jamViewModel.suggest(track)
                    onDismiss()
                }
            }
            onAddToPlaylist?.let {
                SheetAction(Icons.Rounded.LibraryAdd, "Add to playlist") { it(); onDismiss() }
            }
            // One verb, then a question — rather than two rows that ask the user to know
            // Wanda's internal distinction between a public URL and a drop before they have
            // decided who they are sharing with. Shown when either half is available; with no
            // friends and no shareable source there is nothing behind it at all.
            if (onShare != null || dropFriends.isNotEmpty()) {
                SheetAction(Icons.Rounded.Share, "Share") {
                    if (onShare == null) pickingFriend = true else choosingShare = true
                }
            }
            onToggleLike?.let {
                SheetAction(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isLiked) "Remove from liked" else "Add to liked"
                ) { it(); onDismiss() }
            }
            onRemove?.let {
                SheetAction(
                    icon = Icons.Rounded.Delete,
                    label = "Remove from queue",
                    tint = MaterialTheme.colorScheme.error
                ) { it(); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
