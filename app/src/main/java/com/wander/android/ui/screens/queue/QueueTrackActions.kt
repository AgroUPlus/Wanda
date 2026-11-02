package com.wander.android.ui.screens.queue

import androidx.compose.runtime.Composable
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.TrackActionsSheet

/**
 * The long-press sheet for one row of the local queue, shared by the drawer and the queue screen.
 *
 * Acts on the row's [QueueEntry.queueIndex], not on the track's id: the same track can be queued
 * more than once, and looking it up by id removed whichever copy came first rather than the one
 * that was pressed.
 */
@Composable
internal fun QueueTrackActions(
    entry: QueueEntry,
    queue: List<UnifiedTrack>,
    queueViewModel: QueueViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: ((String, String?) -> Unit)?,
    onDismiss: () -> Unit
) {
    val track = entry.track
    TrackActionsSheet(
        track = track,
        isLiked = track.isLiked,
        onPlayNext = {
            queueViewModel.playNext(track)
            onDismiss()
        },
        onAddToQueue = {
            queueViewModel.addToQueue(track)
            onDismiss()
        },
        onStartRadio = {
            queueViewModel.startRadio(track)
            onDismiss()
        },
        onToggleLike = { queueViewModel.toggleLike(track) },
        onRemove = {
            if (entry.isStillAt(queue)) queueViewModel.removeFromQueue(entry.queueIndex)
            onDismiss()
        },
        onOpenArtist = track.artist
            .takeIf { it.isNotBlank() }
            ?.let { onOpenArtist?.let { open -> { name -> open(name, track.artistIdFor(name)) } } },
        onDismiss = onDismiss,
        onShare = if (queueViewModel.canShare(track)) {
            { queueViewModel.share(track) }
        } else null,
        onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
            { addToPlaylist.open(track) }
        } else null
    )
}

/**
 * Whether the queue still holds this entry's track at its index. The sheet can outlive a change to
 * the queue — a track added or removed elsewhere — and an index from before it would hit another row.
 */
internal fun QueueEntry.isStillAt(queue: List<UnifiedTrack>): Boolean =
    queue.getOrNull(queueIndex)?.id == track.id
