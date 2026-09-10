package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A page that opens on the one picture it is about, with its own title set into the foot of it.
 *
 * Every detail page in the app used to lead with a thumbnail: a 96 dp circle for an artist, a
 * 260 dp square for a record, a row of small tiles for the listening statistics. Each was the
 * smallest possible version of the single image that page exists to show. This is the shape they
 * share instead — full width, running to the top of the window under the status bar, fading into
 * `surface` at its foot.
 *
 * **The fade is why the caption needs no scrim.** The text sits inside the part of the gradient
 * that has already reached the theme's own background colour, so it can use `onSurface` and stay
 * legible over any artwork at all. A translucent scrim tuned to darken a pale cover washes out a
 * dark one, and one tuned for a dark cover does nothing for a pale one; there is no single value
 * that works, which is why this does not try to find one.
 *
 * Three stops rather than two, for the same reason: a straight transparent-to-surface ramp spends
 * its whole length visibly greying the picture, while holding the top half clear and doing the
 * work in the bottom half reads as the image sinking into the page.
 *
 * [overlay] is for controls that float on the picture itself — a back arrow, a menu — and is
 * placed in the hero's own `Box`, so callers align it with `Modifier.align`.
 */
@Composable
fun ImmersiveHero(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspect: Float = DefaultAspect,
    scrimHeight: Dp = DefaultScrimHeight,
    horizontalPadding: Dp = 20.dp,
    captionAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    overlay: @Composable BoxScope.() -> Unit = {},
    caption: @Composable ColumnScope.() -> Unit
) {
    ImmersiveHero(
        modifier = modifier,
        aspect = aspect,
        scrimHeight = scrimHeight,
        horizontalPadding = horizontalPadding,
        captionAlignment = captionAlignment,
        overlay = overlay,
        caption = caption,
        backdrop = {
            Artwork(
                url = imageUrl,
                contentDescription = contentDescription,
                // Constant, not measured. A decode size derived from the laid-out box changes on
                // the first frame after measurement, which is a different `ImageRequest` — so Coil
                // decodes a second bitmap of the same picture and swaps it in. Generous enough for
                // a tablet.
                sizeDp = DecodeSize,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
            )
        }
    )
}

/**
 * The same shape, for a page whose subject is not a picture.
 *
 * The social side of the app has nothing to open on: Wanda hosts no uploads, so a person has an
 * avatar and a circle has a handful of them, and stretching either into a banner is a blur. What
 * carries over is not the photograph but the silhouette — full width, running under the status
 * bar, fading into `surface` at its foot with the caption set into the part of the fade that has
 * already arrived. [backdrop] fills the picture's place with whatever the page does have.
 */
@Composable
fun ImmersiveHero(
    modifier: Modifier = Modifier,
    aspect: Float = DefaultAspect,
    scrimHeight: Dp = DefaultScrimHeight,
    horizontalPadding: Dp = 20.dp,
    captionAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    overlay: @Composable BoxScope.() -> Unit = {},
    backdrop: @Composable BoxScope.() -> Unit,
    caption: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier
        .fillMaxWidth()
        .aspectRatio(aspect)
    ) {
        backdrop()

        Column(
            horizontalAlignment = captionAlignment,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.surface
                    )
                )
                .padding(horizontal = horizontalPadding)
                .padding(top = scrimHeight, bottom = 8.dp),
            content = caption
        )

        overlay()
    }
}

/**
 * Slightly taller than wide.
 *
 * A square crop of a publicity photo usually cuts the chin off, and a square cover leaves the
 * caption sitting on the artwork's centre rather than below its subject.
 */
const val DefaultAspect = 0.9f

/** How much of the picture the caption is set over, and therefore how far the fade runs. */
val DefaultScrimHeight = 96.dp

private val DecodeSize = 480.dp
