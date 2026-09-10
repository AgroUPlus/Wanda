package com.wander.android.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.ShapedActionButton
import com.wander.android.ui.components.ShapedPlayButton

/**
 * The top of an artist page: their portrait edge to edge, their name over it, and the things you
 * can do with them.
 *
 * This used to be a rounded card with a 96 dp circular portrait beside two lines of text, laid out
 * to match `DetailHeader` on the album page. The two pages *should* differ here, and now do: an
 * album already shows you its own art in the sleeve, while an artist page opening on a thumbnail
 * of somebody's face is the smallest possible version of the one image the page is about. The
 * portrait is the header now, and the name is set on it.
 *
 * The image runs under the status bar — see `ArtistScreen`, which floats the back control over it
 * rather than reserving a bar above it — and fades into `surface` at its foot, so the page below
 * begins without a seam. The name sits inside that opaque foot rather than over the picture, which
 * is why it can use `onSurface` and stay legible whatever the portrait happens to be: a scrim
 * tuned for a dark photo washes out a pale one, and no scrim at all fails on both.
 *
 * The portrait is the backend's when it publishes one, otherwise a cover off one of their records.
 * Wanda has no artist photography and does not invent any.
 */
@Composable
internal fun ArtistHero(
    name: String,
    subtitle: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null until a track has loaded with a backend artist id — see `ArtistViewModel`. */
    onShare: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PortraitAspect)
        ) {
            Artwork(
                url = imageUrl,
                contentDescription = name,
                // Constant, not measured: a decode size derived from the laid-out box changes on
                // the first frame after measurement and asks Coil for a second bitmap of the same
                // picture. One request, one bitmap. Generous enough for a tablet's width.
                sizeDp = HeroDecodeSize,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().aspectRatio(PortraitAspect)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        // Three stops rather than two. A straight transparent-to-surface ramp
                        // spends its whole length visibly greying the picture; holding the top
                        // half clear and doing the work in the bottom half reads as the portrait
                        // sinking into the page instead of being dimmed by it.
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = ScrimHeight, bottom = 4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp)
        ) {
            ShapedActionButton(
                onClick = onRadio,
                contentDescription = "Start radio",
                icon = Icons.Rounded.Podcasts
            )
            // Centre, and larger than its neighbours: the one control the page exists for. It sat
            // at the trailing edge while the header was a card, which is the right answer for a
            // left-aligned row and the wrong one for a centred portrait.
            ShapedPlayButton(
                onClick = onPlay,
                contentDescription = "Play",
                icon = Icons.Rounded.PlayArrow
            )
            ShapedActionButton(
                onClick = onShuffle,
                contentDescription = "Shuffle",
                icon = Icons.Rounded.Shuffle
            )
            onShare?.let { share ->
                ShapedActionButton(
                    onClick = share,
                    contentDescription = "Share",
                    icon = Icons.Rounded.Share
                )
            }
        }
    }
}

/**
 * Slightly taller than wide. A square crop of a publicity photo usually cuts the chin off; the
 * extra height is what keeps a head-and-shoulders shot intact on a phone.
 */
internal const val PortraitAspect = 0.86f

/** How much of the portrait the name is set over, and therefore how far the fade runs. */
private val ScrimHeight = 108.dp

private val HeroDecodeSize = 480.dp
