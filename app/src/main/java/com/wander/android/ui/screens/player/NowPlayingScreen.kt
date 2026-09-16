package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.screens.social.JamViewModel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.AvatarGroup
import com.wander.android.ui.components.FingerprintBadge
import com.wander.android.ui.components.KeepScreenOn
import com.wander.android.ui.components.scrollingTitle
import androidx.compose.foundation.isSystemInDarkTheme
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.LiveIndicator
import com.wander.android.ui.theme.OnCoverArt
import com.wander.android.ui.theme.rememberCoverSeedColor

/** Nominal edge of the full-screen cover; drives the decode size, not the layout. */
private val FullArtworkSize = 360.dp

/*
 * The rhythm of the block under the artwork.
 *
 * It used to run title → 8dp → bar → 4dp → controls → 8dp → edge, which packed four rows of
 * different weights into the last fifth of the screen and left the gap above the title doing
 * nothing. The artwork above is `weight(1f)`, so every dp given back here is a dp it takes:
 * spacing the block out is also what stops the cover crowding it.
 */

/** How much of the width between the side paddings the cover square actually takes. */
private const val CoverWidthFraction = 0.88f

/**
 * The gap between the rows that belong to the cover: title, artist, bar, transport.
 *
 * Tight, because they are one block — the picture, what it is, where you are in it, and the
 * controls for it. The distance in this stack is not what separates anything; [ActionBarGap] is.
 */
private val PlayerRowGap = 12.dp

/**
 * The gap above the utility group at the foot.
 *
 * Much wider than [PlayerRowGap], and the only real division in the column. Everything above it is
 * about the track playing; the group below is about settings that outlive it — shuffle, repeat,
 * like, the overflow. At an even spacing the transport row sat as close to those as to its own
 * seek bar, so the play button read as the top of the footer rather than the bottom of the player.
 * Widening this is also what lifts the whole stack — cover included — clear of the bottom edge.
 */
private val ActionBarGap = 44.dp


/**
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
 */
@Composable
internal fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenJam: () -> Unit = {},
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
            onSelect = { track ->
                viewModel.setPreferredAudioLanguage(track.language)
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

    // What the immersive branch's own bar and controls actually occupy.
    //
    // Measured rather than written down. They are siblings of the overlay buttons and the lyrics in
    // a `Box`, aligned to opposite edges, so there is no layout relationship to derive a clearance
    // from — and the constants that stood here instead were wrong: the controls column is a title
    // row, a seek bar, transport controls *and* the navigation bar's inset, which is a good deal
    // more than the sum of the paddings it is written with. The lyrics toggle landed on the like
    // button.
    //
    // Reported by `onGloballyPositioned` at the head of each modifier chain, so the figure includes
    // the `safeDrawingPadding` inside it and nothing has to add the system bars back on.
    var immersiveTopBar by remember { mutableStateOf(0.dp) }
    var immersiveControls by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // The cover is the lyrics toggle, so the tap handler outlives any one value of the callback.
    // `pointerInput` is keyed on `Unit` — it must not restart every recomposition, and the lambda
    // the sheet passes is a fresh one each time — so the gesture reads the current callback through
    // this rather than capturing the one that happened to exist when the block was first run.
    val toggleLyrics by rememberUpdatedState(onToggleLyrics)

    if (immersivePlayer && artworkSlot != null) {
        // ── Immersive layout ──────────────────────────────────────────────────────
        // Artwork fills the screen edge-to-edge (MorphingArtwork will match these
        // bounds). Controls sit in a Column at the bottom, readable over the
        // gradient scrim drawn between them.
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(artworkModifier)
                // A tap anywhere on the cover shows the lyrics, and a tap on the lyrics puts the
                // cover back: the lyric lines are children of this box and consume their own taps
                // to seek, so only the space around them reaches this handler.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { toggleLyrics() },
                        onLongPress = { showSpeedPitch = true }
                    )
                }
        ) {
            // Invisible anchor — MorphingArtwork follows these bounds.
            artworkSlot(track.artworkUrl, track.title)

            // Gradient scrim so controls are readable over the artwork.
            //
            // It no longer deepens for the lyrics: they open as their own screen over this one
            // (see `FullScreenLyrics`) rather than being laid over the artwork, so this is back to
            // the one job it had — carrying the title and controls at the foot.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0f),
                                0.45f to Color.Black.copy(alpha = 0.15f),
                                1f to Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    }
            )

            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onGloballyPositioned {
                        immersiveTopBar = with(density) { it.size.height.toDp() }
                    }
                    .fillMaxWidth()
                    // Top and sides only. `safeDrawingPadding()` insets all four edges, so this bar
                    // — which is pinned to the top — was also carrying the navigation bar's inset
                    // along its bottom as dead space. That is invisible on its own, but the height
                    // measured below is what the lyrics are inset by, so it pushed the first line
                    // down by a navigation bar for no reason.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .graphicsLayer { alpha = contentAlpha() }
            ) {
                // No collapse arrow — see the standard layout. Swiping down does it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    val activeJam = jam
                    if (activeJam != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clickable(onClick = onOpenJam)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(LiveIndicator, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Jam",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                AvatarGroup(
                                    usernames = activeJam.members,
                                    size = 18.dp,
                                    overlap = 5.dp,
                                    maxDisplay = 3
                                )
                            }
                        }
                    } else {
                        val canSwitch = viewModel.canSwitchSource(track, state.durationMs)
                        Text(
                            text = track.source.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = OnCoverArt.copy(
                                alpha = if (canSwitch) 1f else 0.75f
                            ),
                            textAlign = TextAlign.Center,
                            modifier = if (canSwitch) {
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        showSourcePicker = true
                                        viewModel.findRenditions(track, state.durationMs)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            } else Modifier
                        )
                    }
                }
                // The overflow lives in `PlayerActionBar` at the foot of the screen now.
            }

            // The share button rides the cover in immersive mode too — but here its parent is the
            // full-bleed Box, not the inset column the standard layout puts it in, so it has to
            // take the window inset itself. Without that it sat in the status bar.
            PlayerOverlayButtons(
                onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                contentAlpha = overlayAlpha,
                topInset = immersiveTopBar
            )

            if (state.audioTracks.size > 1) {
                FilledTonalIconButton(
                    onClick = { showAudioTrackPicker = true },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Start
                            )
                        )
                        .padding(start = 60.dp, top = 4.dp)
                        .graphicsLayer { alpha = overlayAlpha() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Change audio language"
                    )
                }
            }

            // Bottom controls column overlaid on the artwork.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned {
                        immersiveControls = with(density) { it.size.height.toDp() }
                    }
                    .fillMaxWidth()
                    // Bottom and sides only, for the same reason as the top bar: pinned to the
                    // bottom, this column was also insetting its *top* by the status bar. The
                    // lyrics are padded by the height measured here, so that inset sat between the
                    // last lyric line and the title as a band of empty space belonging to nothing —
                    // the gap this layout was reported for.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = contentAlpha() }
            ) {
                // Centred, like the bar and the controls under it. The row that used to hold this
                // to the left was making room for the like button beside it; that moved into
                // `PlayerActionBar`, and left-aligned text over centred controls read as a column
                // that had come loose.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = OnCoverArt,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnCoverArt.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(
                                    enabled = onOpenArtist != null && track.artist.isNotBlank()
                                ) { onOpenArtist?.invoke(track.artist, track.artistId) }
                        )
                        val albumId = track.albumId
                        if (!track.album.isNullOrBlank()) {
                            Text(
                                text = " · ${track.album}",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnCoverArt.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.scrollingTitle()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable(enabled = albumId != null && onOpenAlbum != null) {
                                        albumId?.let { onOpenAlbum?.invoke(it) }
                                    }
                            )
                        }
                    }
                }

                PlayerSeekBar(
                    playerConnection = playerConnection,
                    durationMs = state.durationMs,
                    onSeek = playerConnection::seekTo,
                    modifier = Modifier.padding(top = PlayerRowGap),
                    isLive = track.isLive,
                    isPlaying = state.isPlaying,
                    isSeekable = state.isSeekable
                )

                PlayerControls(
                    state = state,
                    connection = playerConnection,
                    modifier = Modifier.padding(top = PlayerRowGap)
                )

                PlayerActionBar(
                    state = state,
                    connection = playerConnection,
                    isLiked = track.id in likedTrackIds,
                    onToggleLike = { viewModel.toggleLike(track) },
                    onOpenMenu = { showMenuDrawer = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ActionBarGap, bottom = 4.dp)
                )
            }

            if (showSpeedPitch) {
                SpeedPitchPopup(
                    value = speedAndPitch,
                    onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                    onDismiss = { showSpeedPitch = false }
                )
            }
        }
    } else {
        // ── Standard layout ───────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
        // Top action bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            // No collapse arrow. Swiping the player down does it, from anywhere rather than from
            // one 48dp target in the far corner, and the gesture is taught in setup now — see
            // `GesturesStep`. The row is left to centre the source control on its own.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                val activeJam = jam
                if (activeJam != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable(onClick = onOpenJam)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(LiveIndicator, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Jam",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            AvatarGroup(
                                usernames = activeJam.members,
                                size = 18.dp,
                                overlap = 5.dp,
                                maxDisplay = 3
                            )
                        }
                    }
                } else {
                    // The source name is a control, not a caption: the same recording usually
                    // exists in more than one place, and which one plays used to depend entirely
                    // on the list you happened to tap it in.
                    val canSwitch = viewModel.canSwitchSource(track, state.durationMs)
                    Text(
                        text = track.source.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSwitch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = if (canSwitch) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    showSourcePicker = true
                                    viewModel.findRenditions(track, state.durationMs)
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }
            // The overflow moved to `PlayerActionBar` at the foot of the screen. Up here it was
            // the one control on a tall phone that could not be reached without changing grip.
        }

        // Swipeable Artwork / Lyrics Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp, bottom = PlayerRowGap),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    // Short of the full width it is given. The square used to take every pixel
                    // between the 24dp side paddings, which on a tall phone made it the whole
                    // middle of the screen with the title crowded under it. Static, not animated —
                    // `artworkSlot`'s bounds are what the travelling cover follows, so this may
                    // change size but must never be *changing* size. See below.
                    .fillMaxWidth(CoverWidthFraction)
                    .aspectRatio(1f)
                    // No glow here. It belongs behind the cover, and the cover is drawn by the
                    // sheet *before* this screen — so anything painted here is necessarily in
                    // front of it. See the backlight in `MorphingArtwork`.
                    .then(artworkModifier)
                    // `pointerInput` after the swipe modifier, so a horizontal drag still reaches
                    // the skip gesture — only a press that stays put becomes a tap or a long press.
                    //
                    // The tap opens the lyrics, which are their own screen over this one now —
                    // this box only ever holds the cover, so nothing here consumes the tap first.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { toggleLyrics() },
                            onLongPress = { showSpeedPitch = true }
                        )
                    }
            ) {
                // Nothing here may move, scale or resize, because [artworkSlot] is the box whose
                // bounds `PlayerArtworkAnchors` reports and the travelling cover follows — and
                // `graphicsLayer` transforms are included in the coordinates
                // `onGloballyPositioned` hands back.
                if (artworkSlot != null) {
                    artworkSlot(track.artworkUrl, track.title)
                } else {
                    // The dot rides inside the cover rather than beside the title.
                    //
                    // It is a footnote about the track, and the cover is the track — putting it
                    // in the title row gave a six-pixel status the same rank as the song's name.
                    // Bottom-left because artwork is busiest in the middle and album text, when
                    // there is any, tends to sit low-right.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Artwork(
                            url = track.artworkUrl,
                            contentDescription = track.title,
                            sizeDp = FullArtworkSize,
                            shape = MaterialTheme.shapes.extraLarge,
                            crossfade = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        FingerprintBadge(
                            status = fingerprintStatus[track.id] ?: FingerprintStatus.MISSING,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        )
                    }
                }

                if (showSpeedPitch) {
                    SpeedPitchPopup(
                        value = speedAndPitch,
                        onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                        onDismiss = { showSpeedPitch = false }
                    )
                }

                PlayerOverlayButtons(
                    onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                    contentAlpha = overlayAlpha
                )

                // Language button — top-left of the cover, only when multiple audio tracks exist.
                //
                // Gated on `size > 1`: a single-track stream has nothing to switch to, and
                // showing the button for it would open an empty or single-row menu. The button
                // sits at TopStart to mirror the share button at TopEnd and to stay off the album
                // art's visual centre, where artwork tends to be busiest.
                if (state.audioTracks.size > 1) {
                    FilledTonalIconButton(
                        onClick = { showAudioTrackPicker = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .graphicsLayer { alpha = overlayAlpha() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Change audio language"
                        )
                    }
                }
            }
        }

        // Track title & Clickable Artist / Album info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha() }
        ) {
            // Centred on the bar and the controls below it — see the note in the immersive layout.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
                // These open the artist and album pages. They used to run a *search* for the
                // name, which is a list of loosely matching tracks rather than the record or
                // the discography the user was asking to see.
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(
                                enabled = onOpenArtist != null && track.artist.isNotBlank()
                            ) { onOpenArtist?.invoke(track.artist, track.artistId) }
                    )
                    // Only linked when the track carries an album id: without one there is no
                    // page to open, and a tap that goes nowhere is worse than plain text.
                    val albumId = track.albumId
                    if (!track.album.isNullOrBlank()) {
                        Text(
                            text = " · ${track.album}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (albumId != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(enabled = albumId != null && onOpenAlbum != null) {
                                    albumId?.let { onOpenAlbum?.invoke(it) }
                                }
                        )
                    }
                }
            }

            PlayerSeekBar(
                playerConnection = playerConnection,
                durationMs = state.durationMs,
                onSeek = playerConnection::seekTo,
                modifier = Modifier.padding(top = PlayerRowGap),
                isLive = track.isLive,
                isPlaying = state.isPlaying,
                isSeekable = state.isSeekable
            )

            PlayerControls(
                state = state,
                connection = playerConnection,
                modifier = Modifier.padding(top = PlayerRowGap)
            )

            PlayerActionBar(
                state = state,
                connection = playerConnection,
                isLiked = track.id in likedTrackIds,
                onToggleLike = { viewModel.toggleLike(track) },
                onOpenMenu = { showMenuDrawer = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ActionBarGap, bottom = 4.dp)
            )
        }
        }
    }
    } // end CoverTintedTheme
}
