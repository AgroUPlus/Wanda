package com.wander.android.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.LoadingImage
import com.wander.android.ui.components.rememberArtworkRequest

/** How far the cover reaches into the strip from its leading edge before it has faded out. */
internal val MiniCoverBleedWidth = 132.dp

/** The hairline along the strip's bottom edge that stands in for the old ring round the button. */
internal val MiniProgressHeight = 3.dp

/** Where the cover sits for a track with none, clear of the title that starts over the fade. */
private val NoCoverIconInset = 20.dp

/** The track under the fill: a trace of the strip's own content colour, not a second accent. */
private const val PROGRESS_TRACK_ALPHA = 0.12f

/** Where [CoverFade] sits halfway, and how much of the cover it keeps there. */
private const val COVER_FADE_MID_STOP = 0.45f
private const val COVER_FADE_MID_ALPHA = 0.4f

/**
 * The cover's opacity across [MiniCoverBleedWidth]: whole at the strip's edge, down to 40% by
 * the middle — where the title already sits on it — and gone by the far end.
 *
 * A mask rather than a gradient *of the surface colour* laid over the art: the docked strip is
 * transparent over the sheet's cover-tinted background, which no single flat colour matches.
 */
private val CoverFade = Brush.horizontalGradient(
    0f to Color.Black,
    COVER_FADE_MID_STOP to Color.Black.copy(alpha = COVER_FADE_MID_ALPHA),
    1f to Color.Transparent
)

/** [CoverFade] mirrored, for a right-to-left strip, where the cover sits on the right. */
private val CoverFadeRtl = Brush.horizontalGradient(
    0f to Color.Transparent,
    (1f - COVER_FADE_MID_STOP) to Color.Black.copy(alpha = COVER_FADE_MID_ALPHA),
    1f to Color.Black
)

/**
 * The cover art bleeding in from the strip's leading edge and fading into whatever is behind the
 * strip. Clipped by nothing of its own: the strip sits inside the sheet's clipped card, so the
 * card's corners are the cover's corners.
 *
 * Decoded at [MorphArtworkSize], the travelling cover's own size, so the two are the same request
 * and the same cached bitmap — no second decode when the sheet opens and one hands over to the
 * other. [alpha] is read in the draw phase; it changes every frame of a drag.
 */
@Composable
internal fun MiniBleedingCover(url: String?, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Offscreen, so the mask below erases the cover's own pixels rather than the sheet's.
            .graphicsLayer {
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val fade = if (layoutDirection == LayoutDirection.Rtl) CoverFadeRtl else CoverFade
                drawRect(fade, blendMode = BlendMode.DstIn)
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (url.isNullOrBlank()) {
            NoCoverIcon()
        } else {
            LoadingImage(
                model = rememberArtworkRequest(url = url, sizeDp = MorphArtworkSize, crossfade = false),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                error = { NoCoverIcon() }
            )
        }
    }
}

@Composable
private fun BoxScope.NoCoverIcon() {
    Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = NoCoverIconInset)
    )
}

/**
 * Playback progress as a hairline along the strip's bottom edge.
 *
 * [progress] and [alpha] are only read while drawing, and the line has a layer of its own, so a
 * position tick redraws these few pixels and recomposes nothing — the strip is on screen for the
 * whole listening session.
 */
@Composable
internal fun MiniProgressHairline(progress: () -> Float, alpha: () -> Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = PROGRESS_TRACK_ALPHA)
    val fill = MaterialTheme.colorScheme.primary
    Spacer(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha() }
            .drawBehind {
                drawRect(track)
                val filled = size.width * progress().coerceIn(0f, 1f)
                if (filled > 0f) {
                    // Fills from the leading edge, as a progress bar does in either direction.
                    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - filled else 0f
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset(start, 0f),
                        size = Size(filled, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
    )
}
