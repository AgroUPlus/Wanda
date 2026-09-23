package com.wander.android.ui.screens.home

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.graphics.shapes.RoundedPolygon
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork

/**
 * The one shelf on Home that gets a fully expressive treatment instead of [SectionTitle] — the
 * lead shelf, so it reads as a small poster rather than a list label: a gradient display title
 * (see [QuickPicksTitle]), a large round play button beside it, and the first few covers underneath
 * cut into Material shapes and scattered at different sizes, each one a way straight into that
 * track.
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
 * Up to three covers, one large in the middle and two small ones tucked against its corners —
 * placed as fractions of the available width so the cluster keeps its composition on any phone.
 */
@Composable
private fun CoverCluster(
    tracks: List<UnifiedTrack>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ClusterHeight)
    ) {
        val width = maxWidth
        ClusterSlots.take(tracks.size).forEachIndexed { index, slot ->
            val track = tracks[index]
            val size = ClusterHeight * slot.sizeFraction
            ClusterCover(
                track = track,
                shape = slot.shape.toShape(),
                size = size,
                modifier = Modifier
                    .offset(
                        x = (width - size) * slot.x,
                        y = (ClusterHeight - size) * slot.y
                    )
                    .clickable { onPlay(index) }
            )
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
 * The shelf's name as a small poster: a tonal kicker chip ("✦ For you, today") over the title in
 * display type, one word per line with the second stepped in, both filled with one gradient from
 * the theme's primary into its tertiary, and a hand-drawn wave under the last word. Static — no
 * running animation — so the flourish costs nothing once drawn.
 */
@Composable
private fun QuickPicksTitle(title: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val gradient = remember(scheme.primary, scheme.tertiary) {
        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
    }
    val style = MaterialTheme.typography.displayLargeEmphasized.copy(
        brush = gradient,
        lineHeight = 0.92.em,
        letterSpacing = (-0.03).em
    )
    val words = title.split(" ", limit = 2)

    Column(modifier = modifier) {
        Surface(
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            shape = CircleShape,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.home_quick_picks_subtitle),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        words.forEachIndexed { index, word ->
            val last = index == words.lastIndex
            Text(
                text = word,
                style = style,
                modifier = Modifier
                    // Each word steps in from the last — a poster's staggered set, not a list.
                    .padding(start = SecondWordIndent * index)
                    .then(if (last) Modifier.wavyUnderline(gradient) else Modifier)
            )
        }
    }
}

/** A short hand-drawn wave under the text, in [brush] — the flourish that makes it a title. */
private fun Modifier.wavyUnderline(brush: Brush): Modifier = drawBehind {
    val stroke = 4.dp.toPx()
    val wavelength = 18.dp.toPx()
    val amplitude = 3.dp.toPx()
    val y = size.height + 2.dp.toPx()
    val path = Path().apply {
        moveTo(0f, y)
        var x = 0f
        while (x < size.width) {
            val half = wavelength / 2f
            quadraticTo(x + half / 2f, y - amplitude, x + half, y)
            quadraticTo(x + half * 1.5f, y + amplitude, x + wavelength, y)
            x += wavelength
        }
    }
    drawPath(path, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/** Where a cover sits in the cluster: a shape, a size relative to the cluster, and a position. */
private class ClusterSlot(
    val shape: RoundedPolygon,
    val sizeFraction: Float,
    /** 0 = left edge, 1 = right edge. */
    val x: Float,
    /** 0 = top edge, 1 = bottom edge. */
    val y: Float
)

/** Drawn in this order, so the large one comes first and the small ones overlap its corners. */
private val ClusterSlots = listOf(
    ClusterSlot(MaterialShapes.Cookie9Sided, sizeFraction = 1f, x = 0.5f, y = 0f),
    ClusterSlot(MaterialShapes.Circle, sizeFraction = 0.42f, x = 0.02f, y = 0.12f),
    ClusterSlot(MaterialShapes.Clover4Leaf, sizeFraction = 0.46f, x = 0.98f, y = 0.92f)
)

private val ClusterHeight = 220.dp
private val PlayButtonSize = 96.dp
private val SecondWordIndent = 36.dp
