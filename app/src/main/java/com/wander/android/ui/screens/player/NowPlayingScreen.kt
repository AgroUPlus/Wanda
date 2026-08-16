package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.AudioQualityBadge

/** Nominal edge of the full-screen cover; drives the decode size, not the layout. */
private val FullArtworkSize = 360.dp

/**
 * @param artworkSlot fills the cover-art area. By default the screen draws its own artwork; the
 *   player sheet passes a slot that only reserves and reports the space, because it draws a single
 *   artwork that travels between here and the docked strip.
 * @param onLyricsVisibleChange lets the sheet hide that travelling artwork while lyrics are shown.
 * @param artworkModifier applied to the cover-art square. The sheet passes its shared
 *   drag-to-skip gesture here, keeping the *movement* on the artwork it draws itself — if the box
 *   that reports the artwork bounds moved with the finger, the peeking neighbour covers would be
 *   measured against a frame that is itself sliding.
 */
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((String) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    contentAlpha: () -> Float = { 1f },
    artworkSlot: (@Composable (url: String?, contentDescription: String) -> Unit)? = null,
    artworkModifier: Modifier = Modifier,
    onLyricsVisibleChange: (Boolean) -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by playerConnection.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val likedTrackIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    val track = state.currentTrack
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    if (track == null) return

    LaunchedEffect(showLyrics) { onLyricsVisibleChange(showLyrics) }

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
                Text(
                    text = track.source.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                track.audioQualityLabel?.let { quality ->
                    AudioQualityBadge(
                        quality = quality,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // Up here with the other playback-wide switches rather than stranded under the
            // transport row, where it read as an afterthought bolted onto the bottom.
            RadioChip(
                isRadioMode = state.isRadioMode,
                onToggle = playerConnection::toggleRadio,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Open queue")
            }
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

                PlayerOverlayButtons(
                    showLyrics = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onShare = { viewModel.share(track) }.takeIf { viewModel.canShare(track) },
                    contentAlpha = contentAlpha
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
                        overflow = TextOverflow.Ellipsis
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    enabled = onOpenArtist != null && track.artist.isNotBlank()
                                ) { onOpenArtist?.invoke(track.artist) }
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(enabled = albumId != null && onOpenAlbum != null) {
                                        albumId?.let { onOpenAlbum?.invoke(it) }
                                    }
                            )
                        }
                    }
                }

                val isLiked = track.id in likedTrackIds
                IconButton(onClick = { viewModel.toggleLike(track) }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PlayerSeekBar(
                playerConnection = playerConnection,
                durationMs = state.durationMs,
                onSeek = playerConnection::seekTo,
                modifier = Modifier.padding(top = 12.dp)
            )

            PlayerControls(
                state = state,
                connection = playerConnection,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
    }
}
