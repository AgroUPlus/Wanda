package com.wander.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistCredits
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
    val download: String,
    val removeDownload: String,
    val resetProgress: String,
    val moveUp: String,
    val moveDown: String,
    val openAlbum: String,
    val select: String,
    val names: TrackActionNames
)

/** The labels that never change with the track's state; see [trackActionNames]. */
internal class TrackActionNames(
    val playNext: String,
    val queue: String,
    val addToPlaylist: String,
    val radio: String,
    val artist: String,
    val share: String,
    val suggest: String,
    val jam: String
)

@Composable
internal fun trackActionNames() = TrackActionNames(
    playNext = stringResource(R.string.action_play_next),
    queue = stringResource(R.string.action_queue),
    addToPlaylist = stringResource(R.string.action_add_to_playlist),
    radio = stringResource(R.string.menu_radio),
    artist = stringResource(R.string.track_info_artist),
    share = stringResource(R.string.action_share),
    suggest = stringResource(R.string.jam_action_suggest),
    jam = stringResource(R.string.action_jam)
)

/** What each action does; a null callback means the caller does not offer that action. */
internal class TrackActionCallbacks(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleLike: (() -> Unit)?,
    val onShare: (() -> Unit)?,
    val onAddToPlaylist: (() -> Unit)?,
    val onStartRadio: (() -> Unit)?,
    val onOpenArtist: ((String) -> Unit)?,
    /** Given the track's album id; only offered when the track names one. */
    val onOpenAlbum: ((String) -> Unit)?,
    val onRemove: (() -> Unit)?,
    /** Null when the track cannot be downloaded, or already is or is on its way. */
    val onDownload: (() -> Unit)?,
    /** Null when there is no download to remove. */
    val onRemoveDownload: (() -> Unit)?,
    /** Whether [onRemoveDownload] asks first — and so closes the sheet with its own dialog. */
    val confirmsRemoveDownload: Boolean,
    val onResetProgress: (() -> Unit)?,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onSelect: (() -> Unit)? = null
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
    val names = labels.names
    if (state.playable) {
        add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, names.playNext, ActionEmphasis.PRIMARY) { callbacks.onPlayNext(); dismiss() })
        add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, names.queue, ActionEmphasis.SECONDARY) { callbacks.onAddToQueue(); dismiss() })
    }
    addLikeAction(state, labels, callbacks, sheet)
    addShareAction(state, names, callbacks, sheet)
    callbacks.onAddToPlaylist?.let {
        add(MenuAction(Icons.Rounded.LibraryAdd, names.addToPlaylist, ActionEmphasis.ICON) { it(); dismiss() })
    }
    // Not gated on playability either: offline is exactly when a missing download is noticed, and
    // the request waits for the network rather than failing for want of it.
    callbacks.onDownload?.let {
        add(MenuAction(Icons.Rounded.Download, labels.download, ActionEmphasis.ICON) { it(); dismiss() })
    }
    if (state.playable) {
        callbacks.onStartRadio?.let { add(MenuAction(Icons.Rounded.Radio, names.radio) { it(); dismiss() }) }
        callbacks.onOpenArtist?.let { addArtistActions(track.artist, names.artist, it, dismiss) }
    }
    // Not gated on playability: the album page is there to look at whether or not the track
    // would play right now.
    val albumId = track.albumId?.takeIf { it.isNotBlank() }
    if (albumId != null) {
        callbacks.onOpenAlbum?.let { open ->
            add(MenuAction(Icons.Rounded.Album, labels.openAlbum) { open(albumId); dismiss() })
        }
    }
    addJamAction(track, state, names, jamViewModel, dismiss)
    callbacks.onSelect?.let { add(MenuAction(Icons.Rounded.CheckCircle, labels.select, ActionEmphasis.ICON) { it(); dismiss() }) }
    // These ask first, and the confirmation closes the sheet; see `TrackActionsSheet`.
    callbacks.onResetProgress?.let {
        add(MenuAction(Icons.Rounded.RestartAlt, labels.resetProgress, ActionEmphasis.ICON) { it() })
    }
    callbacks.onMoveUp?.let { add(MenuAction(Icons.Rounded.ArrowUpward, labels.moveUp, ActionEmphasis.ICON) { it(); dismiss() }) }
    callbacks.onMoveDown?.let { add(MenuAction(Icons.Rounded.ArrowDownward, labels.moveDown, ActionEmphasis.ICON) { it(); dismiss() }) }
    callbacks.onRemove?.let {
        add(MenuAction(Icons.Rounded.Delete, labels.removeFromQueue, ActionEmphasis.DANGER) { it(); dismiss() })
    }
    callbacks.onRemoveDownload?.let { remove ->
        add(MenuAction(Icons.Rounded.Delete, labels.removeDownload, ActionEmphasis.DANGER) {
            remove()
            if (!callbacks.confirmsRemoveDownload) dismiss()
        })
    }
}

/**
 * "Artist", or one action per name when the credit lists several — opening a page for "A, B & C"
 * found nobody. Where the split may have broken one act's name apart, the whole credit stays first.
 */
private fun MutableList<MenuAction>.addArtistActions(
    credit: String,
    artistLabel: String,
    open: (String) -> Unit,
    dismiss: () -> Unit
) {
    val options = ArtistCredits.choices(credit)
    if (options.size <= 1) {
        add(MenuAction(Icons.Rounded.Person, artistLabel) { open(credit); dismiss() })
        return
    }
    options.take(MAX_ARTIST_ACTIONS).forEach { name ->
        add(MenuAction(Icons.Rounded.Person, name) { open(name); dismiss() })
    }
}

/** Past this many the action sheet stops being a menu and becomes a list of names. */
internal const val MAX_ARTIST_ACTIONS = 4

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
    names: TrackActionNames,
    callbacks: TrackActionCallbacks,
    sheet: TrackActionSheetFlow
) {
    if (callbacks.onShare == null && !state.hasDropFriends) return
    add(MenuAction(Icons.Rounded.Share, names.share, ActionEmphasis.ICON) {
        if (callbacks.onShare == null) sheet.onPickFriend() else sheet.onChooseShare()
    })
}

private fun MutableList<MenuAction>.addJamAction(
    track: UnifiedTrack,
    state: TrackActionState,
    names: TrackActionNames,
    jamViewModel: JamViewModel,
    dismiss: () -> Unit
) {
    val jam = state.jamState.jam ?: return
    val label = if (jam.mode == JamMode.DEMOCRACY) names.suggest else names.jam
    add(MenuAction(Icons.Rounded.Groups, label) { jamViewModel.suggest(track); dismiss() })
}
