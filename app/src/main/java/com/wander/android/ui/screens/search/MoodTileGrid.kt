package com.wander.android.ui.screens.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset
import com.wander.android.ui.components.icon
import com.wander.android.ui.components.label

/** The moods two to a row, each a tile that starts its radio. */
@Composable
internal fun MoodTileGrid(
    moods: List<MoodPreset>,
    playingMoodKey: String?,
    onPlayMood: (MoodPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        moods.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { column, mood ->
                    MoodTile(
                        mood = mood,
                        index = row * 2 + column,
                        playing = mood.key == playingMoodKey,
                        onClick = { onPlayMood(mood) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // An odd last mood keeps its half width rather than stretching across the row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One mood, in its own colour with its own shape bleeding off the top corner, so no two tiles
 * read alike before their labels are read. Pressing squares the corners and turns the shape —
 * the expressive press response — and the playing mood swaps its glyph for an equaliser and
 * gains an outline.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoodTile(
    mood: MoodPreset,
    index: Int,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 16.dp else 28.dp, motion.fastSpatialSpec(), label = "corner")
    val turn by animateFloatAsState(
        targetValue = DecorationTurns[index % DecorationTurns.size] + if (pressed || playing) PRESS_TURN else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "turn"
    )
    val (container, decorationColor) = moodColors(index)
    val decoration = Decorations[index % Decorations.size].toShape()
    val name = mood.label()
    val description = stringResource(R.string.search_mood_radio_start, name)

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = MoodInk,
        border = if (playing) BorderStroke(3.dp, MoodInk) else null,
        modifier = modifier
            .height(120.dp)
            .semantics {
                contentDescription = description
                selected = playing
            }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-24).dp)
                    .size(112.dp)
                    .rotate(turn)
                    .clip(decoration)
                    .background(decorationColor)
            )
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Icon(if (playing) Icons.Rounded.GraphicEq else mood.icon(), contentDescription = null)
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Fixed hues rather than the theme's three containers: four moods over three containers meant
 * two tiles always shared a colour. Light enough that [MoodInk] passes 4.5:1 on each, in either
 * theme; the second of each pair is the same hue a step deeper, for the shape.
 */
private fun moodColors(index: Int): Pair<Color, Color> = MoodPalette[index % MoodPalette.size]

private val MoodPalette = listOf(
    Color(0xFFC9BBEA) to Color(0xFFA291D3), // lavender
    Color(0xFFB9C4F2) to Color(0xFF8C9BDB), // periwinkle
    Color(0xFFF2AE6B) to Color(0xFFD9853A), // apricot
    Color(0xFFEDA1CB) to Color(0xFFD474AA) // pink
)

private val MoodInk = Color(0xFF1C1014)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val Decorations = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.SoftBurst
)

private val DecorationTurns = listOf(0f, 45f, 12f, -30f)
private const val PRESS_TURN = 30f
