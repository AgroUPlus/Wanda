package com.wander.android.ui.screens.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.SleepTimerState
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.KeepScreenOn
import com.wander.android.ui.screens.social.JamViewModel
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.rememberCoverSeedColor
import com.wander.android.data.repository.artistIdFor
import com.wander.android.ui.theme.LocalDarkTheme

/**
 * The Now Playing screen: state collection, the dialogs/sheets that float over it, and the cover
 * tint — then hands the actual layout to [CoverLedPlayerLayout], which used to live inline here
 * before this file crossed 800 lines.
 *
 * @param artworkSlot fills the cover-art area. By default the screen draws its own artwork; the
 *   player sheet passes a slot that only reserves and reports the space, because it draws a single
 *   artwork that travels between here and the docked strip.
 * @param showLyrics whether the lyrics are up over the dimmed cover. Hoisted rather than kept here
 *   because the sheet has to know — it pauses the cover's video clip under them — and because
 *   state owned here outlived the screen: collapsing the player disposes this composable but
 *   `rememberSaveable` restored the flag, so the sheet was left hiding a cover nothing would ever
 *   ask it to show again.
 * @param artworkModifier applied to the cover-art area. The sheet passes its shared
 *   drag-to-skip gesture here, keeping the *movement* on the artwork it draws itself — if the box
 *   that reports the artwork bounds moved with the finger, the peeking neighbour covers would be
 *   measured against a frame that is itself sliding.
 * @param onMinimize collapses the player sheet back to the docked strip — the cover's own
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
     * Alpha for the lyrics pill riding the cover. Separate from [contentAlpha] because the cover it
     * sits on is drawn by the sheet, not by this layout, and it has to leave before the cover does.
     */
    overlayAlpha: () -> Float = contentAlpha,
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)? = null,
    artworkModifier: Modifier = Modifier,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    immersivePlayer: Boolean = false,
    /** A queue index whose title and artist to show ahead of the player; see `PlayerCoverCarousel`. */
    previewIndex: Int? = null,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val likedTrackIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
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
    val isDebugMode by viewModel.isDebugMode.collectAsStateWithLifecycle()
    val streamDebug by viewModel.streamDebug.collectAsStateWithLifecycle()
    val mediaToggle by viewModel.mediaToggle.collectAsStateWithLifecycle()
    // Composed only while the full player is out: lets the toggle look for the other form.
    DisposableEffect(viewModel) { viewModel.setPlayerOpen(true); onDispose { viewModel.setPlayerOpen(false) } }

    if (track == null) return

    val extrasViewModel: EpisodeExtrasViewModel = hiltViewModel()
    val episodeExtras by remember(track.id) { extrasViewModel.extras(track.id) }
        .collectAsStateWithLifecycle(initialValue = null)
    var episodePanel by remember { mutableStateOf<EpisodePanel?>(null) }

    // Opened from the lyrics over the cover, for the controls only the full screen has: the
    // synced/static switch and the timing offset.
    var fullScreenLyrics by remember { mutableStateOf(false) }
    KeepScreenOn(keepAwake = showLyrics || fullScreenLyrics)
    if (isDebugMode) PlaybackDebugOverlay(track, streamDebug, state.durationMs)

    // Extract the dominant colour from the cover art and use it to tint the player surface,
    // if enabled in Settings -> Appearance. Until it arrives or if disabled, base scheme is used.
    val coverSeed = if (isCoverArtThemeEnabled) rememberCoverSeedColor(track.artworkUrl) else null
    val dark = LocalDarkTheme.current
    val base = MaterialTheme.colorScheme

    CoverTintedTheme(seedColor = coverSeed, base = base, dark = dark, amoled = false) {
        // Over the player sheet itself — see `FullScreenLyrics` for why that has to be a dialog.
        // Composed here, inside the cover tint, so it carries the same background the player has rather
        // than the app's untinted one. Closing it goes back to the lyrics over the cover.
        if (fullScreenLyrics) {
            FullScreenLyrics(
                lyrics = lyrics,
                state = state,
                playerConnection = playerConnection,
                onDismiss = { fullScreenLyrics = false },
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
        var showSleepTimer by remember { mutableStateOf(false) }
        val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
        val addToPlaylist = AddToPlaylistHost()
        val jamViewModel: JamViewModel = hiltViewModel()
        val speedAndPitch by playerConnection.speedAndPitch.collectAsStateWithLifecycle()

        // The menu drawer's "Source" row — the one way to it now the top bar's source chip is gone.
        val onOpenSourcePicker: (() -> Unit)? = {
            showSourcePicker = true
            viewModel.findRenditions(track, state.durationMs)
        }.takeIf { viewModel.canSwitchSource(track, state.durationMs) }

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
                isSleepTimerOn = sleepTimerState != SleepTimerState.Off,
                onOpenSleepTimer = { showSleepTimer = true },
                onOpenQueue = onOpenQueue,
                onAddToPlaylist = { addToPlaylist.open(track) },
                onToggleRadio = playerConnection::toggleRadio,
                onOpenSpeedPitch = { showSpeedPitch = true },
                onAddToQueue = { playerConnection.addToQueue(listOf(track)) },
                onToggleLike = { viewModel.toggleLike(track) },
                onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                onOpenSourcePicker = onOpenSourcePicker,
                onOpenAudioTrackPicker = { showAudioTrackPicker = true }.takeIf { state.audioTracks.size > 1 },
                onOpenChapters = { episodePanel = EpisodePanel.CHAPTERS }.takeIf { episodeExtras?.chaptersUrl != null },
                onOpenTranscript = {
                    episodePanel = EpisodePanel.TRANSCRIPT
                }.takeIf { episodeExtras?.transcriptUrl != null },
                onOpenArtist = onOpenArtist?.let { open -> { name -> open(name, track.artistIdFor(name)) } },
                onOpenAlbum = track.albumId?.let { albumId -> onOpenAlbum?.let { open -> { open(albumId) } } },
                onJamAction = jam?.let { { jamViewModel.suggest(track) } },
                equalizerIntent = viewModel.equalizerIntent(),
                onDismiss = { showMenuDrawer = false }
            )
        }

        EpisodePanels(
            panel = episodePanel,
            extras = episodeExtras,
            playerConnection = playerConnection,
            onDismiss = { episodePanel = null }
        )

        if (showSleepTimer) {
            SleepTimerSheet(
                state = sleepTimerState,
                isEpisode = track.isEpisode,
                actions = viewModel.sleepTimerActions,
                onDismiss = { showSleepTimer = false }
            )
        }

        if (showSpeedPitch) {
            SpeedPitchPopup(
                value = speedAndPitch,
                onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                onDismiss = { showSpeedPitch = false }
            )
        }

        CoverLedPlayerLayout(
            immersive = immersivePlayer,
            playerConnection = playerConnection,
            viewModel = viewModel,
            state = state,
            track = track,
            previewIndex = previewIndex,
            isLiked = track.id in likedTrackIds,
            status = PlayerStatus(
                jam = jam,
                mediaToggle = mediaToggle,
                onSwapMediaType = viewModel::swapMediaType,
                sleepTimer = sleepTimerState,
                onOpenJam = onOpenJam
            ),
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            onOpenQueue = onOpenQueue,
            onMinimize = onMinimize,
            onOpenMenu = { showMenuDrawer = true },
            // Hidden under the full-screen dialog: the two share one list state, and two lists
            // driving one state fight over its scroll position.
            showLyrics = showLyrics && !fullScreenLyrics,
            lyrics = {
                CoverLyrics(
                    lyrics = lyrics,
                    playerConnection = playerConnection,
                    trackId = track.id,
                    listState = lyricsListState,
                    letterByLetterEnabled = letterByLetterLyrics,
                    onClose = onToggleLyrics
                )
            },
            onToggleLyrics = onToggleLyrics,
            onOpenFullScreenLyrics = { fullScreenLyrics = true },
            onOpenSpeedPitch = { showSpeedPitch = true },
            contentAlpha = contentAlpha,
            overlayAlpha = overlayAlpha,
            artworkModifier = artworkModifier,
            artworkSlot = artworkSlot,
            modifier = modifier
        )
    } // end CoverTintedTheme
}
