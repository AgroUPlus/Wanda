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
    pressed: QueueEntry,
    entries: List<QueueEntry>,
    queue: List<UnifiedTrack>,
    canReorder: Boolean,
    queueViewModel: QueueViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenArtist: ((String, String?) -> Unit)?,
    onDismiss: () -> Unit
) {
    // The row as it is now: the queue can move under a sheet that stays open.
    val entry = entries.firstOrNull { it.key == pressed.key } ?: pressed
    val track = entry.track
    val (moveUpTo, moveDownTo) = moveStepsFor(entry, entries, canReorder)
    TrackActionsSheet(
        onMoveUp = moveUpTo?.let { to -> { queueViewModel.moveInQueue(entry.queueIndex, to) } },
        onMoveDown = moveDownTo?.let { to -> { queueViewModel.moveInQueue(entry.queueIndex, to) } },
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
 * The queue index of the upcoming row [step] places from the one at [position] in this list of
 * upcoming rows, or null at either end. Only upcoming rows move, and only among themselves — the
 * same rule a drag follows.
 */
internal fun List<QueueEntry>.stepFrom(position: Int, step: Int): Int? =
    getOrNull(position + step)?.queueIndex

/**
 * The single-step moves for [entry], read from the current [entries] by key so a sheet opened a
 * while ago still moves the row from where it is now. Null on both sides when the order cannot be
 * changed or the row is not upcoming.
 */
internal fun moveStepsFor(entry: QueueEntry, entries: List<QueueEntry>, canReorder: Boolean): Pair<Int?, Int?> {
    if (!canReorder) return null to null
    val upcoming = entries.filter { it.role == QueueItemRole.UP_NEXT }
    val position = upcoming.indexOfFirst { it.key == entry.key }
    if (position < 0) return null to null
    return upcoming.stepFrom(position, -1) to upcoming.stepFrom(position, 1)
}

/**
 * Whether the queue still holds this entry's track at its index. The sheet can outlive a change to
 * the queue — a track added or removed elsewhere — and an index from before it would hit another row.
 */
internal fun QueueEntry.isStillAt(queue: List<UnifiedTrack>): Boolean =
    queue.getOrNull(queueIndex)?.id == track.id
