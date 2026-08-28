package com.wander.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.progressOf
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.model.UnifiedTrack
import kotlin.math.abs

/** The edge of the docked strip's cover art. */
val MiniArtworkSize = 48.dp

/**
 * Drag distance at which the strip's text has faded out completely. Matches the swipe's own
 * commit threshold (see `TrackSwipe.kt`), so the text is gone exactly when releasing would skip.
 */
private const val SwipeFadeDistancePx = 120f

/**
 * Fixed height for the progress bar, so the wavy indicator has a stable box to wave inside no
 * matter what its amplitude is.
 *
 * Public because `PlayerSheet` sizes the docked strip from the strip's real parts; keeping the
 * number in one place is what stops the two drifting apart again.
 */
val MiniProgressBarHeight = 12.dp

/** Padding above and below the artwork row. Summed into `MiniPlayerHeight`. */
val MiniRowVerticalPadding = 8.dp

/**
 * The docked strip at the top of the player sheet.
 *
 * It no longer navigates anywhere: the sheet it sits on is dragged open, and this fades out as
 * that happens (see `PlayerSheetContent`).
 *
 * The cover art is *not* drawn here. [artworkSlot] reserves its space and reports its bounds so
 * the sheet can draw one artwork that travels continuously into the full player. [contentAlpha]
 * fades everything else, leaving the cover untouched. It is a lambda so the fade is read
 * during draw rather than composition — the sheet's progress changes every frame.
 *
 * [swipeOffset] is the live drag-to-skip distance, and only the title and artist follow it: the
 * strip's own surface must not slide around over the navigation bar, but a gesture where nothing
 * at all moved read as if the swipe had not registered.
 */
@Composable
fun MiniPlayer(
    track: UnifiedTrack?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    /**
     * The length the *player* reports, not the one the metadata claimed.
     *
     * These disagree, and only one of them is reliable. A YouTube Music row whose subtitle carried
     * no `3:45` reaches Room with `durationMs = 0`, so a progress bar driven from the track ran at
     * zero for the whole song — and since the wave is drawn along the *elapsed* portion, that is a
     * bar with no wave in it at all. The full player never had the bug because it was already
     * reading the player's own duration; this is that same number.
     */
    durationMs: Long,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f },
    swipeOffset: () -> Float = { 0f },
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    artworkSlot: @Composable () -> Unit = {
        Artwork(
            url = track?.artworkUrl,
            contentDescription = null,
            sizeDp = MiniArtworkSize,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(MiniArtworkSize)
        )
    }
) {
    if (track == null) return

    Surface(
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            PlaybackProgressBar(
                playerConnection = playerConnection,
                durationMs = durationMs,
                isPlaying = isPlaying,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = MiniRowVerticalPadding)
            ) {
                artworkSlot()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            translationX = swipeOffset()
                            // Fades as it travels, so the outgoing title does not simply run into
                            // the play button. Reaching zero at the skip threshold means the
                            // gesture's commit point is something you can see.
                            val travel = (abs(swipeOffset()) / SwipeFadeDistancePx).coerceIn(0f, 1f)
                            alpha = contentAlpha() * (1f - travel)
                        }
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                }

                Row(modifier = Modifier.graphicsLayer { alpha = contentAlpha() }) {
                    IconButton(onClick = playerConnection::togglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    IconButton(onClick = playerConnection::next) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                    }
                }
            }
        }
    }
}

/**
 * Kept as its own composable so the twice-a-second position tick recomposes the progress bar
 * alone. Read inside [MiniPlayer] it recomposed the whole docked player while sitting above the
 * scrolling content.
 *
 * The wavy indicator cannot simply stay mounted: its wave is an infinite transition that runs
 * regardless of the progress value, and this strip sits behind Home, Library, Search and Settings
 * whenever a track is loaded — so leaving it mounted meant the app was never idle and every screen
 * paid for a full-rate redraw while scrolling. It also breaks the project's no-polling battery rule.
 *
 * But hard-swapping it for the flat indicator on pause made the line jump: the wave fills the whole
 * box and the flat bar is a thin line centred in it. So the wave's **amplitude** is animated down
 * to zero first, and only once it has settled flat — and playback is still paused — is the wavy
 * indicator unmounted in favour of the flat one it now looks like. Pausing therefore reads as the
 * wave relaxing into a line, and the infinite transition still stops.
 *
 * Which indicator is mounted is *derived* from the amplitude itself ([showWavy]) rather than
 * tracked as a separate flag set imperatively at specific points in the animating coroutine: a
 * flag like that only reaches its "flatten now" line if the coroutine runs to completion
 * uninterrupted, and `isPlaying` flickering rapidly (seen with YTM's network buffering, not
 * Navidrome's steadier stream) cancels and restarts that coroutine before it gets there — leaving
 * the flag stuck saying "still wavy" while nothing is actually drawn. Deriving the decision from
 * the current amplitude value instead means there is no in-between state to get stuck in.
 *
 * Everything sits in a fixed-height box ([MiniProgressBarHeight]) because the two indicators do not
 * measure the same: without it, pausing shrank this row and shifted everything below it up.
 */
@Composable
private fun PlaybackProgressBar(
    playerConnection: PlayerConnection,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val position by rememberPlaybackPosition(playerConnection)
    val progress = { progressOf(position.positionMs, durationMs) }

    val amplitude = remember { Animatable(if (isPlaying) 1f else 0f) }

    // The *fast* effects spec, not the default one. This animation sits directly under a button
    // press, so anything leisurely reads as the tap not having registered rather than as motion —
    // the wave has to start settling on the same frame the icon changes.
    val amplitudeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    LaunchedEffect(isPlaying) {
        amplitude.animateTo(if (isPlaying) 1f else 0f, amplitudeSpec)
    }

    // Wavy the instant playback resumes, even mid-decay; flat only once fully settled and still
    // paused. Reading `.value` here (not through a lambda) is deliberate: it is what makes this
    // recompose — and therefore correct — every frame the amplitude is actually changing, at the
    // cost of those few animated frames instead of the whole idle lifetime of the strip.
    val showWavy = isPlaying || amplitude.value > 0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(MiniProgressBarHeight)
    ) {
        if (showWavy) {
            LinearWavyProgressIndicator(
                progress = progress,
                amplitude = { amplitude.value },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        }
    }
}
