package com.wander.android.ui.screens.player

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.WandaSheet
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.ActionButtonGroup
import com.wander.android.ui.components.ActionEmphasis
import com.wander.android.ui.components.MenuAction
import com.wander.android.ui.components.MenuSheetInfoPageIndex
import com.wander.android.ui.components.MenuSheetPageCount
import com.wander.android.ui.components.MenuSheetPageIndicator
import com.wander.android.ui.components.MenuSheetPager
import com.wander.android.ui.components.TrackInfoPage
import com.wander.android.ui.components.TrackSheetHeader

/**
 * Contextual menu drawer for the Now Playing screen, replacing the former top-right queue button.
 * Unifies access to the queue, playlist actions, radio mode, speed/pitch adjustments, and track actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingMenuDrawer(
    track: UnifiedTrack,
    isLiked: Boolean,
    isRadioMode: Boolean,
    queueSize: Int,
    canSwitchSource: Boolean,
    canShare: Boolean,
    canAddToPlaylist: Boolean,
    hasMultipleAudioTracks: Boolean,
    isSleepTimerOn: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleRadio: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: (() -> Unit)?,
    onOpenSourcePicker: (() -> Unit)?,
    onOpenAudioTrackPicker: (() -> Unit)?,
    onOpenChapters: (() -> Unit)?,
    onOpenTranscript: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onJamAction: (() -> Unit)?,
    onDismiss: () -> Unit
) {
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

                val queueLabel = if (queueSize > 0) {
                    "${stringResource(R.string.action_queue)} ($queueSize)"
                } else {
                    stringResource(R.string.action_queue)
                }
                val radioLabel = stringResource(R.string.menu_radio)
                val strings = MenuStrings(
                    playNext = stringResource(R.string.action_play_next),
                    addToQueue = stringResource(R.string.action_add_to_queue),
                    addToPlaylist = stringResource(R.string.action_add_to_playlist),
                    speed = stringResource(R.string.action_speed_and_pitch),
                    source = stringResource(R.string.action_switch_source),
                    audioLanguage = stringResource(R.string.action_audio_language),
                    jam = stringResource(R.string.action_add_to_jam),
                    share = stringResource(R.string.action_share),
                    like = stringResource(R.string.menu_like),
                    sleepTimer = stringResource(R.string.sleep_timer),
                    chapters = stringResource(R.string.chapters_title),
                    transcript = stringResource(R.string.transcript_title)
                )
                val actions = buildList {
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, queueLabel, ActionEmphasis.PRIMARY) { onOpenQueue(); animatedDismiss() })
                    // Settings, not errands: toggling one keeps the menu open, and the button's own colour
                    // and shape say whether it is on.
                    add(MenuAction(Icons.Rounded.Radio, radioLabel, ActionEmphasis.SECONDARY, selected = isRadioMode) { onToggleRadio() })
                    add(
                        MenuAction(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            strings.like,
                            ActionEmphasis.ICON,
                            selected = isLiked
                        ) { onToggleLike() }
                    )
                    if (canShare && onShare != null) {
                        add(MenuAction(Icons.Rounded.Share, strings.share, ActionEmphasis.ICON) { onShare(); animatedDismiss() })
                    }
                    if (canAddToPlaylist) {
                        add(MenuAction(Icons.Rounded.LibraryAdd, strings.addToPlaylist, ActionEmphasis.ICON) { onAddToPlaylist(); animatedDismiss() })
                    }
                    add(MenuAction(Icons.Rounded.Speed, strings.speed, ActionEmphasis.ICON) { onOpenSpeedPitch(); animatedDismiss() })
                    add(
                        MenuAction(Icons.Rounded.Bedtime, strings.sleepTimer, ActionEmphasis.ICON, selected = isSleepTimerOn) {
                            onOpenSleepTimer(); animatedDismiss()
                        }
                    )
                    onOpenChapters?.let { open ->
                        add(MenuAction(Icons.Rounded.Bookmark, strings.chapters, ActionEmphasis.ICON) { open(); animatedDismiss() })
                    }
                    onOpenTranscript?.let { open ->
                        add(MenuAction(Icons.AutoMirrored.Rounded.Notes, strings.transcript, ActionEmphasis.ICON) { open(); animatedDismiss() })
                    }
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, strings.playNext) { onPlayNext(); animatedDismiss() })
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, strings.addToQueue) { onAddToQueue(); animatedDismiss() })
                    onOpenArtist?.let { add(MenuAction(Icons.Rounded.Person, track.artist) { it(); animatedDismiss() }) }
                    onOpenAlbum?.let { open ->
                        track.album?.takeIf { it.isNotBlank() }?.let { album ->
                            add(MenuAction(Icons.Rounded.Album, album) { open(); animatedDismiss() })
                        }
                    }
                    if (canSwitchSource && onOpenSourcePicker != null) {
                        add(MenuAction(Icons.Rounded.SwapHoriz, strings.source) { onOpenSourcePicker(); animatedDismiss() })
                    }
                    if (hasMultipleAudioTracks && onOpenAudioTrackPicker != null) {
                        add(MenuAction(Icons.Rounded.Translate, strings.audioLanguage) { onOpenAudioTrackPicker(); animatedDismiss() })
                    }
                    onJamAction?.let { add(MenuAction(Icons.Rounded.Groups, strings.jam) { it(); animatedDismiss() }) }
                }

                // Swipe right off the buttons for the same "Info" page `TrackActionsSheet` opens with —
                // this is the current track's own equivalent of that same long-press menu.
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

/** Resolved once in composition, since the action list is built in a plain lambda. */
private class MenuStrings(
    val playNext: String,
    val addToQueue: String,
    val addToPlaylist: String,
    val speed: String,
    val source: String,
    val audioLanguage: String,
    val jam: String,
    val share: String,
    val like: String,
    val sleepTimer: String,
    val chapters: String,
    val transcript: String
)
