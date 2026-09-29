package com.wander.android.ui.components

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
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.ui.screens.social.JamUiState
import com.wander.android.ui.screens.social.JamViewModel

/**
 * Builds the contextual actions available for a track in [TrackActionsSheet].
 */
internal fun buildTrackActionsList(
    track: UnifiedTrack,
    playable: Boolean,
    liked: Boolean,
    likeLabel: String,
    removeFromQueue: String,
    deleteOffline: String,
    jamState: JamUiState,
    jamViewModel: JamViewModel,
    hasDropFriends: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onStartRadio: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onDeleteDownload: (() -> Unit)?,
    onToggleLikedState: () -> Unit,
    onPickFriend: () -> Unit,
    onChooseShare: () -> Unit,
    animatedDismiss: () -> Unit
): List<MenuAction> = buildList {
    if (playable) {
        add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next", ActionEmphasis.PRIMARY) { onPlayNext(); animatedDismiss() })
        add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", ActionEmphasis.SECONDARY) { onAddToQueue(); animatedDismiss() })
    }
    onToggleLike?.let {
        add(
            MenuAction(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = likeLabel,
                emphasis = ActionEmphasis.ICON,
                selected = liked
            ) {
                onToggleLikedState()
                it()
            }
        )
    }
    if (onShare != null || hasDropFriends) {
        add(MenuAction(Icons.Rounded.Share, "Share", ActionEmphasis.ICON) {
            if (onShare == null) onPickFriend() else onChooseShare()
        })
    }
    onAddToPlaylist?.let {
        add(MenuAction(Icons.Rounded.LibraryAdd, "Add to playlist", ActionEmphasis.ICON) { it(); animatedDismiss() })
    }
    if (playable) {
        onStartRadio?.let { add(MenuAction(Icons.Rounded.Radio, "Radio") { it(); animatedDismiss() }) }
        onOpenArtist?.let { add(MenuAction(Icons.Rounded.Person, "Artist") { it(); animatedDismiss() }) }
    }
    jamState.jam?.let { jam ->
        val label = if (jam.mode == JamMode.DEMOCRACY) "Suggest" else "Jam"
        add(MenuAction(Icons.Rounded.Groups, label) { jamViewModel.suggest(track); animatedDismiss() })
    }
    onRemove?.let {
        add(MenuAction(Icons.Rounded.Delete, removeFromQueue, ActionEmphasis.DANGER) { it(); animatedDismiss() })
    }
    onDeleteDownload?.let {
        add(MenuAction(Icons.Rounded.Delete, deleteOffline, ActionEmphasis.DANGER) { it(); animatedDismiss() })
    }
}
