package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.scrollingTitle
import com.wander.android.ui.theme.OnCoverArt

/**
 * The immersive Now Playing layout: artwork fills the screen edge-to-edge (the travelling cover
 * follows [artworkSlot]'s bounds), controls sit in a column at the foot over a gradient scrim.
 *
 * Split out of `NowPlayingScreen` — see that file for why — with every value this needs to draw
 * threaded in explicitly rather than closed over.
 */
@Composable
internal fun ImmersivePlayerLayout(
    playerConnection: PlayerConnection,
    viewModel: NowPlayingViewModel,
    state: PlaybackState,
    track: UnifiedTrack,
    jam: Jam?,
    likedTrackIds: Set<String>,
    onOpenJam: () -> Unit,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenQueue: () -> Unit,
    onMinimize: () -> Unit,
    onOpenMenu: () -> Unit,
    onToggleLyrics: () -> Unit,
    contentAlpha: () -> Float,
    overlayAlpha: () -> Float,
    artworkModifier: Modifier,
    artworkSlot: @Composable (url: String?, contentDescription: String) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onOpenAudioTrackPicker: () -> Unit,
    showSpeedPitch: Boolean,
    onOpenSpeedPitch: () -> Unit,
    onDismissSpeedPitch: () -> Unit,
    speedAndPitch: SpeedAndPitch,
    modifier: Modifier = Modifier
) {
    // What the top bar actually occupies — measured rather than written down, since
    // `PlayerOverlayButtons`'s inset has to match the bar's real height exactly.
    var immersiveTopBar by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // `pointerInput` is keyed on `Unit` — it must not restart every recomposition, and the lambda
    // the sheet passes is a fresh one each time — so the gesture reads the current callback
    // through this rather than capturing the one that happened to exist when the block first ran.
    val toggleLyrics by rememberUpdatedState(onToggleLyrics)

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
                    onLongPress = { onOpenSpeedPitch() }
                )
            }
    ) {
        // Invisible anchor — MorphingArtwork follows these bounds.
        artworkSlot(track.artworkUrl, track.title)

        // Gradient scrim so controls are readable over the artwork.
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

        PlayerTopBar(
            jam = jam,
            sourceLabel = track.source.displayName,
            canSwitchSource = viewModel.canSwitchSource(track, state.durationMs),
            onOpenSourcePicker = onOpenSourcePicker,
            onOpenJam = onOpenJam,
            onMinimize = onMinimize,
            onOpenQueue = onOpenQueue,
            sourceLabelColor = OnCoverArt,
            sourceLabelColorMuted = OnCoverArt.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .onGloballyPositioned {
                    immersiveTopBar = with(density) { it.size.height.toDp() }
                }
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .graphicsLayer { alpha = contentAlpha() }
        )

        // The share button rides the cover in immersive mode too — but here its parent is the
        // full-bleed Box, not the inset column the standard layout puts it in, so it has to take
        // the window inset itself. Without that it sat in the status bar.
        PlayerOverlayButtons(
            onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
            contentAlpha = overlayAlpha,
            topInset = immersiveTopBar
        )

        if (state.audioTracks.size > 1) {
            FilledTonalIconButton(
                onClick = onOpenAudioTrackPicker,
                shapes = IconButtonDefaults.shapes(),
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
                    contentDescription = stringResource(R.string.common_change_audio_language)
                )
            }
        }

        // Bottom controls column overlaid on the artwork.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .graphicsLayer { alpha = contentAlpha() }
        ) {
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
                onOpenMenu = onOpenMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ActionBarGap, bottom = 4.dp)
            )
        }

        if (showSpeedPitch) {
            SpeedPitchPopup(
                value = speedAndPitch,
                onChange = { playerConnection.setSpeedAndPitch(it.speed, it.pitch) },
                onDismiss = onDismissSpeedPitch
            )
        }
    }
}
