package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.AudioQualityBadge
import com.wander.android.ui.components.AvatarGroup
import com.wander.android.ui.components.FingerprintBadge
import com.wander.android.ui.components.LikeButton
import com.wander.android.ui.components.scrollingTitle
import com.wander.android.ui.theme.LiveIndicator
import com.wander.android.ui.theme.OnCoverArt

/** Nominal edge of the full-screen cover; drives the decode size, not the layout. */
private val FullArtworkSize = 360.dp

/**
 * How much of the immersive layout's top and bottom edges are already spoken for.
 *
 * The immersive branch draws its own top bar and its own controls column straight onto the cover,
 * both of them inset off the system bars. Anything else floating on that cover — the overlay
 * buttons, a verse of lyrics — has to clear them, and there is no layout relationship to derive
 * that from: they are siblings in a `Box`, aligned to opposite edges. Measuring them to find out
 * would cost a layout pass per frame of the sheet's drag, so these are the heights they are built
 * from, and they move together with the paddings in the branch itself.
 */
private val ImmersiveTopBarHeight = 56.dp

/** Title row, seek bar and transport controls, plus the column's own vertical padding. */
private val ImmersiveControlsInset = 172.dp

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
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((String, String?) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenJam: () -> Unit = {},
    contentAlpha: () -> Float = { 1f },
    /**
     * Alpha for the two buttons floating over the cover. Separate from [contentAlpha] because the
     * cover they sit on is drawn by the sheet, not by this layout — see [PlayerOverlayButtons].
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
    val track = state.currentTrack

    if (track == null) return

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

    // Long-pressing the cover opens speed and pitch at the point that was touched.
    var rateAnchor by remember { mutableStateOf<IntOffset?>(null) }
    val speedAndPitch by playerConnection.speedAndPitch.collectAsStateWithLifecycle()

    if (immersivePlayer && artworkSlot != null) {
        // ── Immersive layout ──────────────────────────────────────────────────────
        // Artwork fills the screen edge-to-edge (MorphingArtwork will match these
        // bounds). Controls sit in a Column at the bottom, readable over the
        // gradient scrim drawn between them.
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(artworkModifier)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { rateAnchor = IntOffset(it.x.toInt(), it.y.toInt()) }
                    )
                }
        ) {
            // Invisible anchor — MorphingArtwork follows these bounds.
            artworkSlot(track.artworkUrl, track.title)

            // Gradient scrim so controls are readable over the artwork.
            //
            // It deepens towards a flat wash while the lyrics are up: the cover stays where it is
            // in this layout rather than fading away, so the lyrics need something of their own to
            // sit on, and a gradient tuned to carry two lines of title at the foot leaves the top
            // of a verse on bare artwork. Animated so the toggle is a dim rather than a cut, and
            // read inside `drawBehind` so it costs a draw rather than a recomposition per frame.
            val lyricsScrim by animateFloatAsState(
                targetValue = if (showLyrics) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "immersive-lyrics-scrim"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = lerp(0f, 0.62f, lyricsScrim)),
                                0.45f to Color.Black.copy(alpha = lerp(0.15f, 0.68f, lyricsScrim)),
                                1f to Color.Black.copy(alpha = lerp(0.72f, 0.78f, lyricsScrim))
                            )
                        )
                    }
            )

            // The lyrics themselves. Absent from this branch entirely until now: the toggle in
            // `PlayerOverlayButtons` was drawn and wired, but nothing below it ever rendered
            // `SyncedLyricsView`, so turning lyrics on faded the cover out and put nothing in its
            // place.
            //
            // Inset past the top bar and the controls column so a long verse scrolls between them
            // instead of under them.
            val lyricsEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(lyricsEffects),
                exit = fadeOut(lyricsEffects),
                modifier = Modifier.fillMaxSize()
            ) {
                SyncedLyricsView(
                    state = lyrics,
                    playerConnection = playerConnection,
                    onSeek = playerConnection::seekTo,
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(top = ImmersiveTopBarHeight, bottom = ImmersiveControlsInset)
                        .graphicsLayer { alpha = contentAlpha() }
                )
            }

            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .graphicsLayer { alpha = contentAlpha() }
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = "Close player",
                        tint = OnCoverArt
                    )
                }
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
                        track.audioQualityLabel?.let { quality ->
                            AudioQualityBadge(
                                quality = quality,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                QueueRadioButton(
                    isRadioMode = state.isRadioMode,
                    onOpenQueue = onOpenQueue,
                    onToggleRadio = playerConnection::toggleRadio
                )
            }

            // Overlay buttons (lyrics / share) ride the cover in immersive mode too — but here
            // their parent is the full-bleed Box, not the inset column the standard layout puts
            // them in, so they have to take the window insets themselves. Without that the share
            // button sat in the status bar, and the lyrics toggle on top of the play controls.
            PlayerOverlayButtons(
                showLyrics = showLyrics,
                onToggleLyrics = onToggleLyrics,
                onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                contentAlpha = overlayAlpha,
                applyWindowInsets = true,
                topInset = ImmersiveTopBarHeight,
                bottomInset = ImmersiveControlsInset
            )

            if (state.audioTracks.size > 1) {
                FilledTonalIconButton(
                    onClick = { showAudioTrackPicker = true },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
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
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = contentAlpha() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = OnCoverArt,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
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
                    LikeButton(
                        isLiked = track.id in likedTrackIds,
                        onToggle = { viewModel.toggleLike(track) },
                        size = 28.dp
                    )
                }

                PlayerSeekBar(
                    playerConnection = playerConnection,
                    durationMs = state.durationMs,
                    onSeek = playerConnection::seekTo,
                    modifier = Modifier.padding(top = 12.dp),
                    isLive = track.isLive
                )

                PlayerControls(
                    state = state,
                    connection = playerConnection,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            rateAnchor?.let { anchor ->
                SpeedPitchPopup(
                    value = speedAndPitch,
                    onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                    onDismiss = { rateAnchor = null },
                    offset = anchor
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
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Close player")
            }
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
                    track.audioQualityLabel?.let { quality ->
                        AudioQualityBadge(
                            quality = quality,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            // Radio folded into a long press here rather than carrying its own labelled chip,
            // which cost a whole slot in the bar to say something the icon tint can say.
            QueueRadioButton(
                isRadioMode = state.isRadioMode,
                onOpenQueue = onOpenQueue,
                onToggleRadio = playerConnection::toggleRadio
            )
        }

        // Swipeable Artwork / Lyrics Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxSize()
                    .then(artworkModifier)
                    // `pointerInput` after the swipe modifier, so a horizontal drag still reaches
                    // the skip gesture — only a press that stays put becomes a long press.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { rateAnchor = IntOffset(it.x.toInt(), it.y.toInt()) }
                        )
                    }
            ) {
                // Opacity only. Nothing here may move, scale or resize, because [artworkSlot] is
                // the box whose bounds `PlayerArtworkAnchors` reports and the travelling cover
                // follows — and `graphicsLayer` transforms are included in the coordinates
                // `onGloballyPositioned` hands back. A `SizeTransform` did it by resizing, and a
                // `scaleIn`/`scaleOut` of even 0.98 did it by transform: either way the cover
                // spends the transition chasing a target that is itself shrinking, which is what
                // made the toggle look broken rather than smooth.
                //
                // `using null` disables the size transform for the same reason. The spec comes
                // from the motion scheme rather than a hand-rolled spring.
                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using null },
                    label = "lyrics-artwork",
                    modifier = Modifier.fillMaxSize()
                ) { lyricsVisible ->
                    if (lyricsVisible) {
                        SyncedLyricsView(
                            state = lyrics,
                            playerConnection = playerConnection,
                            onSeek = playerConnection::seekTo,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = contentAlpha() }
                        )
                    } else if (artworkSlot != null) {
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
                }

                rateAnchor?.let { anchor ->
                    SpeedPitchPopup(
                        value = speedAndPitch,
                        onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                        onDismiss = { rateAnchor = null },
                        offset = anchor
                    )
                }

                PlayerOverlayButtons(
                    showLyrics = showLyrics,
                    onToggleLyrics = onToggleLyrics,
                    onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                    contentAlpha = overlayAlpha
                )

                // Language button — top-left of the cover, only when multiple audio tracks exist.
                //
                // Gated on `size > 1`: a single-track stream has nothing to switch to, and
                // showing the button for it would open an empty or single-row menu. The button
                // sits at TopStart to mirror the overlay buttons (Share/Lyrics) at TopEnd/BottomEnd
                // and to stay off the album art's visual centre, where artwork tends to be busiest.
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    // These open the artist and album pages. They used to run a *search* for the
                    // name, which is a list of loosely matching tracks rather than the record or
                    // the discography the user was asking to see.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
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

                LikeButton(
                    isLiked = track.id in likedTrackIds,
                    onToggle = { viewModel.toggleLike(track) },
                    size = 28.dp
                )
            }

            PlayerSeekBar(
                playerConnection = playerConnection,
                durationMs = state.durationMs,
                onSeek = playerConnection::seekTo,
                modifier = Modifier.padding(top = 12.dp),
                isLive = track.isLive
            )

            PlayerControls(
                state = state,
                connection = playerConnection,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
        }
    }
}
