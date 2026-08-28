package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.LikeButton
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.AudioQualityBadge
import com.wander.android.ui.components.scrollingTitle

/** Nominal edge of the full-screen cover; drives the decode size, not the layout. */
private val FullArtworkSize = 360.dp

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
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val likedTrackIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    var showSourcePicker by remember { mutableStateOf(false) }
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
                                    .background(androidx.compose.ui.graphics.Color(0xFFEF4444), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Jam",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            com.wander.android.ui.components.AvatarGroup(
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

        // Long-pressing the cover opens speed and pitch at the point that was touched. Kept off
        // the top bar deliberately: it is an adjustment made mid-listen and does not deserve a
        // permanent slot next to the controls that are used every time.
        var rateAnchor by remember { mutableStateOf<IntOffset?>(null) }
        val speedAndPitch by playerConnection.speedAndPitch.collectAsStateWithLifecycle()

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
                            lyrics = lyrics,
                            playerConnection = playerConnection,
                            onSeek = playerConnection::seekTo,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = contentAlpha() }
                        )
                    } else if (artworkSlot != null) {
                        artworkSlot(track.artworkUrl, track.title)
                    } else {
                        Artwork(
                            url = track.artworkUrl,
                            contentDescription = track.title,
                            sizeDp = FullArtworkSize,
                            shape = MaterialTheme.shapes.extraLarge,
                            crossfade = true,
                            modifier = Modifier.fillMaxSize()
                        )
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
