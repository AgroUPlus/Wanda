package com.wander.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.ui.theme.heroOverline

/**
 * A page that opens on the one picture it is about, with its title on a sheet that rises over the
 * foot of it.
 *
 * Every detail page in the app used to lead with a thumbnail: a 96 dp circle for an artist, a
 * 260 dp square for a record. Each was the smallest possible version of the single image that page
 * exists to show. This is the shape they share instead — full width, running to the top of the
 * window under the status bar — and the one the statistics screen opens on too.
 *
 * **The sheet is why the caption needs no scrim.** The text sits on `surface`, not on the
 * artwork, so it can use `onSurface` and stay legible over any picture at all. A translucent scrim
 * tuned to darken a pale cover washes out a dark one; there is no single value that works. The
 * sheet's rounded top edge is also what makes the hand-off read as a page laid over the picture
 * rather than a picture that fades away.
 *
 * [overlay] is for controls that float on the picture itself — a back arrow, a menu — and is
 * placed in the picture's own `Box`, so callers align it with `Modifier.align`.
 *
 * **The overlay is inset off the status bar and the cutout here, not by its callers.** The picture
 * runs to the top of the window, underneath the system bars; anything drawn on top of it therefore
 * starts underneath them too, and a caller who forgets lands a back arrow in the status bar. Top
 * and sides only: the bottom of the picture is in the middle of the page, nowhere near the
 * navigation bar.
 */
@Composable
fun ImmersiveHero(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspect: Float = DefaultAspect,
    horizontalPadding: Dp = 24.dp,
    captionAlignment: Alignment.Horizontal = Alignment.Start,
    overlay: @Composable BoxScope.() -> Unit = {},
    caption: @Composable ColumnScope.() -> Unit
) {
    ImmersiveHero(
        modifier = modifier,
        aspect = aspect,
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
 * bar, with the caption on a sheet rising over its foot. [backdrop] fills the picture's place
 * with whatever the page does have.
 */
@Composable
fun ImmersiveHero(
    modifier: Modifier = Modifier,
    aspect: Float = DefaultAspect,
    horizontalPadding: Dp = 24.dp,
    captionAlignment: Alignment.Horizontal = Alignment.Start,
    overlay: @Composable BoxScope.() -> Unit = {},
    backdrop: @Composable BoxScope.() -> Unit,
    caption: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
            backdrop()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
            ) {
                overlay()
            }
        }
        Surface(
            shape = HeroSheetShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().riseBy(HeroSheetOverlap)
        ) {
            Column(
                horizontalAlignment = captionAlignment,
                modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 24.dp),
                content = caption
            )
        }
    }
}

/** The small coloured line above a hero's title that says what kind of thing the page is about. */
@Composable
fun HeroOverline(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.heroOverline,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/**
 * Draws this [rise] higher *and* reports itself that much shorter, so what follows in the list moves
 * up with it. A plain `offset` would move only the drawing and leave a gap of the same size below.
 */
fun Modifier.riseBy(rise: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = rise.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/** The title sheet's rounded top — M3 Expressive's extra-large-increased corner. */
val HeroSheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

/** How far the title sheet rises over the picture. */
val HeroSheetOverlap = 40.dp

/** Slightly taller than wide: a square crop of a publicity photo usually cuts the chin off. */
const val DefaultAspect = 0.9f

private val DecodeSize = 480.dp
