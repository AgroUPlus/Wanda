package com.wander.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.ui.components.selection.SelectedArtworkMark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    /**
     * Whether this row paints its own "now playing" background. False in the queue, where the
     * whole grouped cell changes colour instead — see `QueueUpNext`.
     */
    showBackground: Boolean = true,
    /** Long press. Null leaves the row without a context menu, as in the queue's reorder mode. */
    onLongPress: (() -> Unit)? = null,
    /**
     * Whether tapping the row would actually play something. Defaults to the offline rule, so
     * every list dims the same tracks without being told to; the queue passes its own value.
     */
    enabled: Boolean = track.isPlayableNow(),
    /**
     * Whether this track has been fingerprinted. Null draws nothing at all.
     *
     * Null by default so the dozens of lists that have no opinion on it are unchanged, and so the
     * status is never fetched by a row that is not showing it — see `FingerprintStatusRepository`
     * on why this arrives as one map for a whole screen rather than a query per row.
     */
    fingerprintStatus: FingerprintStatus? = null,
    /**
     * A small label drawn under the like button/equalizer, right-aligned. Null draws nothing.
     *
     * Added for History's per-play time (`HH:mm`) rather than folded into [subtitle]: every other
     * caller's subtitle describes the *track* (artist, album, duration), while this describes the
     * *play* — a fact only History has, and everyone else has nothing to put here.
     */
    trailingLabel: String? = null,
    /**
     * Where an imported placeholder is in being matched to a playable track. Null for every real
     * track, which is every row outside an import.
     */
    matchStatus: MatchStatus? = null,
    /**
     * Null outside selection mode. Inside it, whether this row is one of the selected — and a tap
     * reaches [onPlay] even on a row that would not play, since the caller toggles it instead.
     */
    selected: Boolean? = null
) {
    // Dimming the whole row rather than each piece: the row is one object, and fading the parts
    // separately made the artwork and the text disagree about how unavailable the track was.
    val rowAlpha = if (enabled) 1f else DisabledAlpha

    // The whole row answers a press, the way a playlist row does: it shrinks under the finger,
    // text and all, while its cover relaxes into a circle. Every list in the app is built from
    // this row or one shaped like it, so they all answer a press the same way.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by rememberPressScale(interactionSource, label = "trackRowPress")
    val artworkShape = rememberShelfArtworkShape(isPressed)
    val rowBackground by animateColorAsState(
        targetValue = if (selected == true) {
            MaterialTheme.colorScheme.secondaryContainer
        } else if (isPlaying && showBackground) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "trackRowBackground"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(rowBackground, MaterialTheme.shapes.medium)
            // Long press stays live while the tap does not: the actions sheet is still useful on
            // an unplayable track — it is where "download" and "add to playlist" live.
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (enabled || selected != null) onPlay() },
                onLongClickLabel = stringResource(R.string.common_more_options),
                onLongClick = onLongPress
            )
            .then(if (selected != null) Modifier.semantics { this.selected = selected == true } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                sizeDp = 52.dp,
                shape = artworkShape,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer { alpha = rowAlpha }
            )
            if (selected == true) SelectedArtworkMark(Modifier.matchParentSize())
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .graphicsLayer { alpha = rowAlpha }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    // `weight` rather than the bare scrolling modifier: the dot needs a reserved
                    // place, and without this a long title would push it off the row entirely.
                    modifier = Modifier.weight(1f, fill = false).scrollingTitle()
                )
                fingerprintStatus?.let {
                    Spacer(Modifier.width(6.dp))
                    FingerprintBadge(it)
                }
            }
            // No quality badge here: it belongs to the track you are listening to, not to every
            // row in every list. Now Playing is the one place that shows it.
            // The badge leads the subtitle rather than replacing it: which station this is still
            // matters, and a livestream's subtitle is where the channel name lives. Static here —
            // see [LiveChip]. Without it a live row was indistinguishable from an hour-long
            // recording until you played it and found you could not scrub.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (track.isLive) {
                    LiveChip(pulsing = false)
                    Spacer(Modifier.width(6.dp))
                }
                TrackDownloadBadge(track)
                Text(
                    text = track.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            when {
                matchStatus == MatchStatus.MATCHING -> {
                    val finding = stringResource(R.string.track_matching)
                    LoadingIndicator(modifier = Modifier.size(24.dp).semantics { contentDescription = finding })
                }
                matchStatus == MatchStatus.NOT_FOUND -> Text(
                    text = stringResource(R.string.track_not_found),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                isPlaying -> KineticEqualizer(isPlaying = true)
                // A heart mid-selection would be a second tap target that does something else.
                onToggleLike != null && selected == null -> LikeButton(isLiked = track.isLiked, onToggle = onToggleLike)
            }
            trailingLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = if (onToggleLike == null && !isPlaying) 4.dp else 0.dp)
                )
            }
        }
    }
}

/** Material's disabled-content opacity. */
private const val DisabledAlpha = 0.38f

/** "Artist · Album · 3:41", skipping whatever the source did not provide. */
private fun UnifiedTrack.subtitle(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    album?.takeIf { it.isNotBlank() },
    durationFormatted.takeIf { durationMs > 0 }
).joinToString(" · ")
