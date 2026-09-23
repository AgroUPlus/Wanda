package com.wander.android.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork

/**
 * The one shelf on Home that gets a fully expressive treatment instead of [SectionTitle] — the
 * lead shelf, so it reads as a small poster rather than a list label: a heavy display title
 * (see [QuickPicksTitle]), a large round play button beside it, and the first few covers underneath
 * cut into Material shapes at different sizes, each one a way straight into that track.
 */
@Composable
internal fun QuickPicksHeader(
    title: String,
    tracks: List<UnifiedTrack>,
    onPlay: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            QuickPicksTitle(title = title, modifier = Modifier.weight(1f))

            FilledIconButton(
                onClick = { onPlay(0) },
                enabled = tracks.isNotEmpty(),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(PlayButtonSize)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.common_play_all),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        if (tracks.isNotEmpty()) {
            CoverCluster(tracks = tracks, onPlay = onPlay, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

/**
 * Up to three covers in a row — a small one, a large one, a small one — staggered up and down so
 * they still read as scattered. Sizes come from the available width, with [ClusterGap] reserved
 * between neighbours, so the covers never touch on any phone.
 */
@Composable
private fun CoverCluster(
    tracks: List<UnifiedTrack>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Read here: the inner Box's scope cannot reach `maxWidth` implicitly.
        val width = maxWidth
        val large = width * LargeCoverFraction
        val small = (width - large - ClusterGap * 2) / 2
        Box(modifier = Modifier.fillMaxWidth().height(large)) {
            ClusterSlots.take(tracks.size).forEachIndexed { index, slot ->
                val size = if (slot.column == 1) large else small
                val x = when (slot.column) {
                    0 -> 0.dp
                    1 -> small + ClusterGap
                    else -> width - small
                }
                ClusterCover(
                    track = tracks[index],
                    shape = slot.shape.toShape(),
                    size = size,
                    modifier = Modifier
                        .offset(
                            x = x,
                            y = (large - size) * slot.y
                        )
                        .clickable { onPlay(index) }
                )
            }
        }
    }
}

@Composable
private fun ClusterCover(track: UnifiedTrack, shape: Shape, size: Dp, modifier: Modifier) {
    Box(modifier = modifier.size(size)) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = size,
            shape = shape,
            crossfade = true,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * The shelf's name set like a poster headline: [PosterFontFamily] at 64sp, one word per line,
 * leading and tracking pulled in until the lines nearly touch, in plain `onSurface` so it reads on
 * any wallpaper-derived scheme. Size and weight do the work.
 */
@Composable
private fun QuickPicksTitle(title: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontFamily = PosterFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 0.86.em,
        letterSpacing = (-0.02).em
    )

    Box(modifier = modifier) {
        Text(
            text = title.replaceFirst(" ", "\n"),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier.stretchX(TitleStretch)
        )
    }
}

/** Where a cover sits in the cluster: a shape, a column (0 left, 1 centre, 2 right), a height. */
private class ClusterSlot(
    val shape: RoundedPolygon,
    val column: Int,
    /** 0 = top edge, 1 = bottom edge. */
    val y: Float
)

/** The first track takes the large centre slot; the next two flank it, one high and one low. */
private val ClusterSlots = listOf(
    ClusterSlot(MaterialShapes.Cookie9Sided, column = 1, y = 0f),
    ClusterSlot(MaterialShapes.Circle, column = 0, y = 0.1f),
    ClusterSlot(MaterialShapes.Clover4Leaf, column = 2, y = 0.9f)
)

/**
 * The bundled Google Sans Flex pushed to a poster setting: high on its `wght` axis and with
 * `ROND` at 0. The app's rounded title face is right for labels, but a headline this size reads as
 * a poster only when the corners are sharp and the strokes nearly fill the counters.
 */
private val PosterFontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(PosterWeight),
            FontVariation.Setting("ROND", 0f)
        )
    )
)

/**
 * Between `Bold` and `ExtraBold`: heavy enough to carry the poster, short of the dense end of the
 * axis where the counters close up and the word reads as a block.
 */
private const val PosterWeight = 750

/**
 * How much wider the title is drawn than set. The bundled Google Sans Flex carries only the `wght`
 * and `ROND` axes — no `wdth` — so the extended look comes from scaling, pushed to a wide poster
 * stance (1.30x) that fits the shelf header beside the play button across standard screen widths.
 */
private const val TitleStretch = 1.30f

/**
 * Draws the content [factor] times wider from its leading edge, and *measures* it that wide too —
 * a bare `graphicsLayer` scale would leave the layout at the unscaled width and let the title run
 * under the play button beside it.
 */
private fun Modifier.stretchX(factor: Float): Modifier = this
    .layout { measurable, constraints ->
        val narrowed = constraints.copy(
            minWidth = (constraints.minWidth / factor).toInt(),
            maxWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth / factor).toInt()
            } else {
                constraints.maxWidth
            }
        )
        val placeable = measurable.measure(narrowed)
        layout((placeable.width * factor).roundToInt(), placeable.height) { placeable.place(0, 0) }
    }
    .graphicsLayer {
        scaleX = factor
        transformOrigin = TransformOrigin(0f, 0.5f)
    }

/** Share of the width the centre cover takes; the two flanking covers split what is left. */
private const val LargeCoverFraction = 0.5f

/** Clear space kept between neighbouring covers. */
private val ClusterGap = 12.dp
private val PlayButtonSize = 96.dp
