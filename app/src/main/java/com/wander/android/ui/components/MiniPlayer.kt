package com.wander.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.player.MiniButtonSize
import com.wander.android.ui.components.player.MiniPlayButton
import kotlin.math.abs

/** The edge of the docked strip's cover art. */
val MiniArtworkSize = 48.dp

/**
 * Drag distance at which the strip's text has faded out completely. Matches the swipe's own
 * commit threshold (see `TrackSwipe.kt`), so the text is gone exactly when releasing would skip.
 */
private const val SwipeFadeDistancePx = 120f

/** Padding above and below the artwork row. Summed into `MiniStripHeight`. */
val MiniRowVerticalPadding = 8.dp

private val MiniButtonGap = 4.dp

/** Tonal, not solid: the strip is translucent over the sheet and this should not fight it. */
private const val MiniButtonContainerAlpha = 0.55f

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
     * no `3:45` reaches Room with `durationMs = 0`, so a progress ring driven from the track ran at
     * zero for the whole song. The full player never had the bug because it was already reading the
     * player's own duration; this is that same number.
     */
    durationMs: Long,
    /** Whether the engine is still fetching audio — see [PlayPauseIcon]. */
    isBuffering: Boolean = false,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = MiniRowVerticalPadding)
        ) {
            artworkSlot()

            Column(
                verticalArrangement = Arrangement.Center,
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

            // Each button gets a container of its own. Bare glyphs on the strip sat directly
            // on whatever the sheet's cover tint happened to be that track, which is a colour
            // chosen by the artwork rather than for legibility — a pale sleeve left them
            // barely there. A tonal shape behind each one is a constant background to read
            // against, and it gives the two targets a visible edge on a strip where they are
            // otherwise a pair of icons floating next to the title.
            //
            // The *glyphs* on it are the empty/outline pair, not the containers — see
            // `PlayPauseIcon`'s own doc on `outlined`. The container stays solid because a
            // hollow button that small is hard to find by touch; only the icon inside it reads
            // as the lighter mark this strip asked for.
            //
            // The 48dp minimum-touch-target wrapper is switched off for these: it lays a 40dp
            // button out at 40dp but *places* it as if the box were 48, shifting the glyph 4dp
            // down-right of its own layout box — which is what pushed the play button off the
            // centre of the progress ring around it.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MiniButtonGap),
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha() }
                ) {
                    MiniPlayButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        playerConnection = playerConnection,
                        durationMs = durationMs
                    )
                    val nextInteraction = remember { MutableInteractionSource() }
                    val nextScale by rememberPressScale(nextInteraction)
                    FilledTonalIconButton(
                        onClick = playerConnection::next,
                        interactionSource = nextInteraction,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                .copy(alpha = MiniButtonContainerAlpha)
                        ),
                        modifier = Modifier
                            .size(MiniButtonSize)
                            .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.action_next))
                    }
                }
            }
        }
    }
}
