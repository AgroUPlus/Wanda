package com.wander.android.ui.screens.player

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.KeepScreenOn
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.rememberCoverSeedColor

/**
 * The gap between the rows that belong to the cover: title, artist, bar, transport.
 *
 * Tight, because they are one block — the picture, what it is, where you are in it, and the
 * controls for it. The distance in this stack is not what separates anything; [ActionBarGap] is.
 * `internal` rather than `private`: both [ImmersivePlayerLayout] and [StandardPlayerLayout] use it.
 */
internal val PlayerRowGap = 12.dp

/**
 * The gap above the utility group at the foot.
 *
 * Much wider than [PlayerRowGap], and the only real division in the column. Everything above it is
 * about the track playing; the group below is about settings that outlive it — shuffle, repeat,
 * like, the overflow.
 */
internal val ActionBarGap = 44.dp

/**
 * The Now Playing screen: state collection, the dialogs/sheets that float over it, and the cover
 * tint — then dispatches to [ImmersivePlayerLayout] or [StandardPlayerLayout] for the actual
 * layout, which used to live inline here before this file crossed 800 lines.
 *
 * @param artworkSlot fills the cover-art area. By default the screen draws its own artwork; the
 *   player sheet passes a slot that only reserves and reports the space, because it draws a single
 *   artwork that travels between here and the docked strip.
 * @param showLyrics whether the lyrics are up instead of the cover. Hoisted rather than kept here
 *   because the sheet has to know — it draws the travelling artwork this replaces — and because
 *   state owned here outlived the screen: collapsing the player disposes this composable but
 *   `rememberSaveable` restored the flag, so the sheet was left hiding a cover nothing would ever
 *   ask it to show again.
 * @param artworkModifier applied to the cover-art square. The sheet passes its shared
 *   drag-to-skip gesture here, keeping the *movement* on the artwork it draws itself — if the box
 *   that reports the artwork bounds moved with the finger, the peeking neighbour covers would be
 *   measured against a frame that is itself sliding.
 * @param onMinimize collapses the player sheet back to the docked strip — the top bar's own
 *   button, distinct from the swipe-down gesture which still works everywhere else on the sheet.
 */
@Composable
internal fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenJam: () -> Unit = {},
    onMinimize: () -> Unit = {},
    contentAlpha: () -> Float = { 1f },
    /**
     * Alpha for the share button floating over the cover. Separate from [contentAlpha] because the
     * cover it sits on is drawn by the sheet, not by this layout — see [PlayerOverlayButtons].
     */
    overlayAlpha: () -> Float = contentAlpha,
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)? = null,
    artworkModifier: Modifier = Modifier,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    immersivePlayer: Boolean = false,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val likedTrackIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    val fingerprintStatus by viewModel.fingerprintStatus.collectAsStateWithLifecycle()
    var showSourcePicker by remember { mutableStateOf(false) }
    var showAudioTrackPicker by remember { mutableStateOf(false) }
    val renditions by viewModel.renditions.collectAsStateWithLifecycle()
    val isFindingRenditions by viewModel.isFindingRenditions.collectAsStateWithLifecycle()
    val jam by viewModel.jam.collectAsStateWithLifecycle()
    val isCoverArtThemeEnabled by viewModel.isCoverArtThemeEnabled.collectAsStateWithLifecycle()
    val letterByLetterLyrics by viewModel.isLetterByLetterLyricsEnabled.collectAsStateWithLifecycle()
    // Hoisted here rather than inside `SyncedLyricsView`: the lyrics now live in a dialog that is
    // composed only while it is open, so state owned down there — scroll position among it — would
    // be lost every time it closed. This screen stays composed across that, so holding it here is
    // what lets reopening the lyrics find them exactly where they were left.
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val track = state.currentTrack

    if (track == null) return

    KeepScreenOn(keepAwake = showLyrics)

    // Extract the dominant colour from the cover art and use it to tint the player surface,
    // if enabled in Settings -> Appearance. Until it arrives or if disabled, base scheme is used.
    val coverSeed = if (isCoverArtThemeEnabled) rememberCoverSeedColor(track.artworkUrl) else null
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme

    CoverTintedTheme(seedColor = coverSeed, base = base, dark = dark, amoled = false) {

    // Over both layouts, and over the player sheet itself — see `FullScreenLyrics` for why that
    // has to be a dialog. Composed here, inside the cover tint, so it carries the same background
    // the player has rather than the app's untinted one.
    if (showLyrics) {
        FullScreenLyrics(
            lyrics = lyrics,
            state = state,
            playerConnection = playerConnection,
            onDismiss = onToggleLyrics,
            listState = lyricsListState,
            letterByLetterEnabled = letterByLetterLyrics
        )
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            current = track,
            renditions = renditions,
            isSearching = isFindingRenditions,
            onSelect = { rendition ->
                // Read at the moment of the swap, not when the picker opened — the song has been
                // playing the whole time the search was running.
                viewModel.playFrom(rendition, playerConnection.currentPositionMs() ?: 0L)
                showSourcePicker = false
                viewModel.clearRenditions()
            },
            onDismiss = {
                showSourcePicker = false
                viewModel.clearRenditions()
            }
        )
    }

    if (showAudioTrackPicker) {
        AudioTrackPickerDialog(
            audioTracks = state.audioTracks,
            onSelect = { pickedTrack ->
                viewModel.setPreferredAudioLanguage(pickedTrack.language)
                showAudioTrackPicker = false
            },
            onDismiss = { showAudioTrackPicker = false }
        )
    }

    var showSpeedPitch by remember { mutableStateOf(false) }
    var showMenuDrawer by remember { mutableStateOf(false) }
    val addToPlaylist = AddToPlaylistHost()
    val jamViewModel: JamViewModel = hiltViewModel()
    val speedAndPitch by playerConnection.speedAndPitch.collectAsStateWithLifecycle()

    if (showMenuDrawer) {
        NowPlayingMenuDrawer(
            track = track,
            isLiked = track.id in likedTrackIds,
            isRadioMode = state.isRadioMode,
            queueSize = state.queue.size,
            canSwitchSource = viewModel.canSwitchSource(track, state.durationMs),
            canShare = viewModel.canShare(track),
            canAddToPlaylist = addToPlaylist.canAdd(track),
            hasMultipleAudioTracks = state.audioTracks.size > 1,
            onOpenQueue = onOpenQueue,
            onAddToPlaylist = { addToPlaylist.open(track) },
            onToggleRadio = playerConnection::toggleRadio,
            onOpenSpeedPitch = { showSpeedPitch = true },
            onPlayNext = { playerConnection.playNext(listOf(track)) },
            onAddToQueue = { playerConnection.addToQueue(listOf(track)) },
            onToggleLike = { viewModel.toggleLike(track) },
            onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
            onOpenSourcePicker = {
                showSourcePicker = true
                viewModel.findRenditions(track, state.durationMs)
            }.takeIf { viewModel.canSwitchSource(track, state.durationMs) },
            onOpenAudioTrackPicker = { showAudioTrackPicker = true }.takeIf { state.audioTracks.size > 1 },
            onOpenArtist = onOpenArtist?.let { open -> { open(track.artist, track.artistId) } },
            onOpenAlbum = track.albumId?.let { albumId -> onOpenAlbum?.let { open -> { open(albumId) } } },
            onJamAction = jam?.let { { jamViewModel.suggest(track) } },
            onDismiss = { showMenuDrawer = false }
        )
    }

    val onOpenAudioTrackPicker: () -> Unit = { showAudioTrackPicker = true }
    val onOpenSpeedPitch: () -> Unit = { showSpeedPitch = true }
    val onDismissSpeedPitch: () -> Unit = { showSpeedPitch = false }
    val onOpenMenu: () -> Unit = { showMenuDrawer = true }

    if (immersivePlayer && artworkSlot != null) {
        ImmersivePlayerLayout(
            playerConnection = playerConnection,
            viewModel = viewModel,
            state = state,
            track = track,
            jam = jam,
            likedTrackIds = likedTrackIds,
            onOpenJam = onOpenJam,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            onOpenQueue = onOpenQueue,
            onMinimize = onMinimize,
            onOpenMenu = onOpenMenu,
            onToggleLyrics = onToggleLyrics,
            contentAlpha = contentAlpha,
            overlayAlpha = overlayAlpha,
            artworkModifier = artworkModifier,
            artworkSlot = artworkSlot,
            onOpenAudioTrackPicker = onOpenAudioTrackPicker,
            showSpeedPitch = showSpeedPitch,
            onOpenSpeedPitch = onOpenSpeedPitch,
            onDismissSpeedPitch = onDismissSpeedPitch,
            speedAndPitch = speedAndPitch,
            modifier = modifier
        )
    } else {
        StandardPlayerLayout(
            playerConnection = playerConnection,
            viewModel = viewModel,
            state = state,
            track = track,
            jam = jam,
            likedTrackIds = likedTrackIds,
            fingerprintStatus = fingerprintStatus,
            onOpenJam = onOpenJam,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            onOpenQueue = onOpenQueue,
            onMinimize = onMinimize,
            onOpenMenu = onOpenMenu,
            onToggleLyrics = onToggleLyrics,
            contentAlpha = contentAlpha,
            overlayAlpha = overlayAlpha,
            artworkModifier = artworkModifier,
            artworkSlot = artworkSlot,
            onOpenAudioTrackPicker = onOpenAudioTrackPicker,
            showSpeedPitch = showSpeedPitch,
            onOpenSpeedPitch = onOpenSpeedPitch,
            onDismissSpeedPitch = onDismissSpeedPitch,
            speedAndPitch = speedAndPitch,
            modifier = modifier
        )
    }
    } // end CoverTintedTheme
}
