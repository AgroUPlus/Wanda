package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.FingerprintBadge
import com.wander.android.ui.components.scrollingTitle

/** Nominal edge of the full-screen cover; drives the decode size, not the layout. */
private val FullArtworkSize = 360.dp

/** How much of the width between the side paddings the cover square actually takes. */
private const val CoverWidthFraction = 0.88f

/**
 * The standard Now Playing layout: a column running top bar → artwork/lyrics → title → seek bar →
 * transport → action bar, used whenever the immersive edge-to-edge layout isn't in play.
 *
 * Split out of `NowPlayingScreen` — see that file for why — with every value this needs to draw
 * threaded in explicitly rather than closed over.
 */
@Composable
internal fun StandardPlayerLayout(
    playerConnection: PlayerConnection,
    viewModel: NowPlayingViewModel,
    state: PlaybackState,
    track: UnifiedTrack,
    jam: Jam?,
    likedTrackIds: Set<String>,
    fingerprintStatus: Map<String, FingerprintStatus>,
    onOpenJam: () -> Unit,
    onOpenArtist: ((String, String?) -> Unit)?,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenQueue: () -> Unit,
    onMinimize: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSourcePicker: (() -> Unit)?,
    onToggleLyrics: () -> Unit,
    contentAlpha: () -> Float,
    overlayAlpha: () -> Float,
    artworkModifier: Modifier,
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)?,
    onOpenAudioTrackPicker: () -> Unit,
    showSpeedPitch: Boolean,
    onOpenSpeedPitch: () -> Unit,
    onDismissSpeedPitch: () -> Unit,
    speedAndPitch: SpeedAndPitch,
    modifier: Modifier = Modifier
) {
    val toggleLyrics by rememberUpdatedState(onToggleLyrics)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        PlayerTopBar(
            jam = jam,
            sourceLabel = track.source.displayName,
            onOpenJam = onOpenJam,
            onMinimize = onMinimize,
            onOpenQueue = onOpenQueue,
            onOpenSourcePicker = onOpenSourcePicker,
            sourceLabelColorMuted = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
        )

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
                    .fillMaxWidth(CoverWidthFraction)
                    .aspectRatio(1f)
                    .then(artworkModifier)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { toggleLyrics() },
                            onLongPress = { onOpenSpeedPitch() }
                        )
                    }
            ) {
                if (artworkSlot != null) {
                    artworkSlot(track.artworkUrl, track.title)
                } else {
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
                        onDismiss = onDismissSpeedPitch
                    )
                }

                PlayerOverlayButtons(
                    onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                    contentAlpha = overlayAlpha
                )

                // Language button — top-left of the cover, only when multiple audio tracks exist.
                if (state.audioTracks.size > 1) {
                    FilledTonalIconButton(
                        onClick = onOpenAudioTrackPicker,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .graphicsLayer { alpha = overlayAlpha() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = stringResource(R.string.common_change_audio_language)
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
            TrackTextTransition(track = track, queueIndex = state.currentIndex) { shown ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = shown.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
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
                            text = shown.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.scrollingTitle()
                                // The name is the way to the artist's page, from the one screen
                                // that is always about the track playing.
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(
                                    enabled = onOpenArtist != null && shown.artist.isNotBlank()
                                ) { onOpenArtist?.invoke(shown.artist, shown.artistId) }
                        )
                        val albumId = shown.albumId
                        if (!shown.album.isNullOrBlank()) {
                            Text(
                                text = " · ${shown.album}",
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
    }
}
