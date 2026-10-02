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
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.ui.screens.social.JamUiState
import com.wander.android.ui.screens.social.JamViewModel

/** What the sheet knows about the track and session when it builds its actions. */
internal class TrackActionState(
    val playable: Boolean,
    val liked: Boolean,
    val hasDropFriends: Boolean,
    val jamState: JamUiState
)

/** Text for the actions whose labels depend on a string resource. */
internal class TrackActionLabels(
    val like: String,
    val removeFromQueue: String,
    val deleteOffline: String,
    val resetProgress: String
)

/** What each action does; a null callback means the caller does not offer that action. */
internal class TrackActionCallbacks(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleLike: (() -> Unit)?,
    val onShare: (() -> Unit)?,
    val onAddToPlaylist: (() -> Unit)?,
    val onStartRadio: (() -> Unit)?,
    val onOpenArtist: (() -> Unit)?,
    val onRemove: (() -> Unit)?,
    val onDeleteDownload: (() -> Unit)?,
    val onResetProgress: (() -> Unit)?
)

/** The sheet's own state changes an action can trigger. */
internal class TrackActionSheetFlow(
    val onToggleLikedState: () -> Unit,
    val onPickFriend: () -> Unit,
    val onChooseShare: () -> Unit,
    val animatedDismiss: () -> Unit
)

/**
 * Builds the contextual actions available for a track in [TrackActionsSheet].
 */
internal fun buildTrackActionsList(
    track: UnifiedTrack,
    state: TrackActionState,
    labels: TrackActionLabels,
    callbacks: TrackActionCallbacks,
    jamViewModel: JamViewModel,
    sheet: TrackActionSheetFlow
): List<MenuAction> = buildList {
    val dismiss = sheet.animatedDismiss
    if (state.playable) {
        add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next", ActionEmphasis.PRIMARY) { callbacks.onPlayNext(); dismiss() })
        add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", ActionEmphasis.SECONDARY) { callbacks.onAddToQueue(); dismiss() })
    }
    addLikeAction(state, labels, callbacks, sheet)
    addShareAction(state, callbacks, sheet)
    callbacks.onAddToPlaylist?.let {
        add(MenuAction(Icons.Rounded.LibraryAdd, "Add to playlist", ActionEmphasis.ICON) { it(); dismiss() })
    }
    if (state.playable) {
        callbacks.onStartRadio?.let { add(MenuAction(Icons.Rounded.Radio, "Radio") { it(); dismiss() }) }
        callbacks.onOpenArtist?.let { add(MenuAction(Icons.Rounded.Person, "Artist") { it(); dismiss() }) }
    }
    addJamAction(track, state, jamViewModel, dismiss)
    // These two ask first, and the confirmation closes the sheet; see `TrackActionsSheet`.
    callbacks.onResetProgress?.let {
        add(MenuAction(Icons.Rounded.RestartAlt, labels.resetProgress, ActionEmphasis.ICON) { it() })
    }
    callbacks.onRemove?.let {
        add(MenuAction(Icons.Rounded.Delete, labels.removeFromQueue, ActionEmphasis.DANGER) { it(); dismiss() })
    }
    callbacks.onDeleteDownload?.let {
        add(MenuAction(Icons.Rounded.Delete, labels.deleteOffline, ActionEmphasis.DANGER) { it() })
    }
}

private fun MutableList<MenuAction>.addLikeAction(
    state: TrackActionState,
    labels: TrackActionLabels,
    callbacks: TrackActionCallbacks,
    sheet: TrackActionSheetFlow
) {
    val toggleLike = callbacks.onToggleLike ?: return
    add(
        MenuAction(
            icon = if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = labels.like,
            emphasis = ActionEmphasis.ICON,
            selected = state.liked
        ) {
            sheet.onToggleLikedState()
            toggleLike()
        }
    )
}

private fun MutableList<MenuAction>.addShareAction(
    state: TrackActionState,
    callbacks: TrackActionCallbacks,
    sheet: TrackActionSheetFlow
) {
    if (callbacks.onShare == null && !state.hasDropFriends) return
    add(MenuAction(Icons.Rounded.Share, "Share", ActionEmphasis.ICON) {
        if (callbacks.onShare == null) sheet.onPickFriend() else sheet.onChooseShare()
    })
}

private fun MutableList<MenuAction>.addJamAction(
    track: UnifiedTrack,
    state: TrackActionState,
    jamViewModel: JamViewModel,
    dismiss: () -> Unit
) {
    val jam = state.jamState.jam ?: return
    val label = if (jam.mode == JamMode.DEMOCRACY) "Suggest" else "Jam"
    add(MenuAction(Icons.Rounded.Groups, label) { jamViewModel.suggest(track); dismiss() })
}
