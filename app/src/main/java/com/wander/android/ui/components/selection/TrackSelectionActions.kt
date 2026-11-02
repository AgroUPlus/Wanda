package com.wander.android.ui.components.selection

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.isPlayableOffline
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.LocalOfflinePlayback
import com.wander.android.ui.components.TrackDownloadViewModel

/** One thing the selection bar can do with the selected rows. */
class SelectionAction(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val onClick: (List<SelectedTrack>) -> Unit
)

/**
 * The batch actions that apply to [selection] right now, most used first, followed by the
 * screen's own [extra] ones. As in the track sheet, an action none of the rows supports is absent
 * rather than present and doing nothing: no queueing when nothing selected would play, no "Add to
 * playlist" across sources (a playlist lives on one), no Download when every row already is.
 */
@Composable
internal fun rememberSelectionActions(
    selection: TrackSelectionState,
    addToPlaylist: AddToPlaylistController?,
    extra: List<SelectionAction>
): List<SelectionAction> {
    val batch: TrackSelectionViewModel = hiltViewModel()
    val downloads: TrackDownloadViewModel = hiltViewModel()
    val liked by batch.likedIds.collectAsStateWithLifecycle()
    val marks by downloads.marks.collectAsStateWithLifecycle()
    val offline = LocalOfflinePlayback.current
    val tracks = selection.tracks
    val playable = tracks.filter { it.source != SourceType.UNRESOLVED && (!offline || it.isPlayableOffline()) }
    val allLiked = tracks.isNotEmpty() && tracks.all { it.id in liked }
    val source = tracks.map { it.source }.distinct().singleOrNull()

    return buildList {
        if (playable.isNotEmpty()) {
            add(SelectionAction(Icons.Rounded.PlayArrow, R.string.action_play) { batch.play(playable) })
            add(SelectionAction(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.action_play_next) { batch.playNext(playable) })
            add(SelectionAction(Icons.AutoMirrored.Rounded.QueueMusic, R.string.action_add_to_queue) { batch.addToQueue(playable) })
        }
        if (addToPlaylist != null && source != null && addToPlaylist.canAdd(tracks.first())) {
            add(SelectionAction(Icons.Rounded.LibraryAdd, R.string.action_add_to_playlist) {
                addToPlaylist.openForTracks(tracks, source)
            })
        }
        add(
            SelectionAction(
                icon = if (allLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (allLiked) R.string.action_unlike else R.string.action_like
            ) { batch.setLiked(tracks, liked = !allLiked) }
        )
        if (tracks.any { downloads.canDownload(it, marks) }) {
            add(SelectionAction(Icons.Rounded.Download, R.string.track_action_download) { downloads.download(tracks) })
        }
        if (tracks.any { downloads.canRemove(it, marks) }) {
            add(SelectionAction(Icons.Rounded.Delete, R.string.track_action_remove_download) { downloads.remove(tracks) })
        }
        addAll(extra)
    }
}
