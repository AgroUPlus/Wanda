package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The player, reduced to the parts a gesture lands on — same cover width fraction and shape as
 * [com.wander.android.ui.screens.player.NowPlayingScreen]'s real one, a real (if fictional) title
 * and artist rather than grey bars, a seek bar, and a control row, so this reads as *that* screen,
 * not a placeholder. The cover falls back to the same look [com.wander.android.ui.components.Artwork]
 * does — a flat tinted square with a centred note glyph — for the same reason: it is fictional
 * content, not a real cover, so it should look exactly as honest as the real fallback does.
 *
 * [coverOverlay] lets the skip gesture draw its own two-cover carousel in the same slot, at the
 * same size and shape, rather than duplicating the cover box's own layout.
 *
 * Used only by [GesturePreview], which layers gesture-specific overlays and the animated fingertip
 * on top of this.
 */
@Composable
internal fun PhoneMock(
    highlight: GestureMotion,
    modifier: Modifier = Modifier,
    coverOverlay: (@Composable BoxScope.() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // The cover is the target of three of the five gestures, so it brightens for those — the
    // difference between "swipe the cover" and "swipe the player" is the whole distinction.
    val coverIsTarget = highlight != GestureMotion.SWIPE_UP && highlight != GestureMotion.SWIPE_DOWN

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(CoverWidthFraction)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .let { if (coverOverlay == null) it.background(coverColor(scheme, coverIsTarget)) else it },
            contentAlignment = Alignment.Center
        ) {
            if (coverOverlay != null) {
                coverOverlay()
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = coverIconColor(scheme, coverIsTarget)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        MockTrackText(
            title = stringResource(R.string.welcome_gesture_mock_track_title),
            artist = stringResource(R.string.welcome_gesture_mock_track_artist)
        )
        SeekBarSkeleton(scheme)
        ControlRow(scheme)
    }
}

/** The docked mini player a gesture leaves the full frame collapsed into. */
@Composable
internal fun MiniPlayerMock(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(MiniArtworkSize)
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.welcome_gesture_mock_track_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.welcome_gesture_mock_track_artist),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, tint = scheme.primary)
        Spacer(modifier = Modifier.width(SmallGap))
        Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun MockTrackText(title: String, artist: String) {
    Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        text = artist,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** prev / play / next, sized and coloured like the real [com.wander.android.ui.screens.player.PlayerControls]. */
@Composable
private fun ControlRow(scheme: ColorScheme) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = scheme.onSurfaceVariant)
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = scheme.onPrimary)
        }
        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

/** A seek bar, not just a line: a track, a filled portion, and a thumb at the end of it. */
@Composable
private fun SeekBarSkeleton(scheme: ColorScheme) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Bar(widthFraction = 1f, height = SeekTrackHeight, color = scheme.onSurface.copy(alpha = 0.16f))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(SeekProgress)
                    .height(SeekTrackHeight)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.7f))
            )
            Box(
                modifier = Modifier
                    .size(SeekThumbSize)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
            Spacer(modifier = Modifier.weight(1f - SeekProgress))
        }
    }
}

@Composable
private fun Bar(
    widthFraction: Float,
    height: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(CircleShape)
            .background(color)
    )
}

private fun coverColor(scheme: ColorScheme, isTarget: Boolean): Color =
    if (isTarget) scheme.primaryContainer else scheme.surfaceContainerHigh

internal fun coverIconColor(scheme: ColorScheme, isTarget: Boolean): Color =
    if (isTarget) scheme.primary else scheme.onSurfaceVariant

internal const val CoverWidthFraction = 0.88f
private const val SeekProgress = 0.35f
private val SeekTrackHeight = 4.dp
private val SeekThumbSize = 12.dp
private val SmallGap = 8.dp
private val MiniArtworkSize = 40.dp
