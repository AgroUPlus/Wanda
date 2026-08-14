package com.wander.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
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

/** The edge of the docked strip's cover art. */
val MiniArtworkSize = 48.dp

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
 */
@Composable
fun MiniPlayer(
    track: UnifiedTrack?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f },
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
                durationMs = track.durationMs,
                isPlaying = isPlaying,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                artworkSlot()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer { alpha = contentAlpha() }
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
 * The wavy indicator is mounted **only while playing**. Its wave is an infinite transition that
 * runs regardless of the progress value, and this strip sits behind Home, Library, Search and
 * Settings whenever a track is loaded — so leaving it mounted meant the app was never idle and
 * every screen paid for a full-rate redraw while scrolling. It also breaks the project's
 * no-polling battery rule.
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

    if (isPlaying) {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = progress, modifier = modifier.fillMaxWidth())
    }
}
