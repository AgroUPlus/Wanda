package com.wander.android.ui.screens.artist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which of the theme's own tonal pairs a button draws from — see [ArtistActionButton]'s [tint].
 *
 * Every one of these already exists in `WandaLightScheme`/`WandaDarkScheme`
 * ([com.wander.android.ui.theme.WandaLightScheme]): this is a palette choice, not a new colour.
 */
internal enum class ActionTint { PRIMARY_SOLID, PRIMARY_CONTAINER, SECONDARY_CONTAINER, TERTIARY_CONTAINER, NEUTRAL }

@Composable
private fun ActionTint.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        // The one solid, non-container colour — the hero play button, the single control the
        // page exists for. Everything else stays on a tonal container, including a toggle that's
        // switched on: a solid `primary` pill there would fight the hero for the same emphasis.
        ActionTint.PRIMARY_SOLID -> scheme.primary to scheme.onPrimary
        ActionTint.PRIMARY_CONTAINER -> scheme.primaryContainer to scheme.onPrimaryContainer
        ActionTint.SECONDARY_CONTAINER -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ActionTint.TERTIARY_CONTAINER -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ActionTint.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurface
    }
}

/**
 * One button in either row — see the doc above the rows themselves for the weight mechanism.
 *
 * The shape mechanism is `ActionButtonGroup`'s own, not a polygon morph: a full stadium
 * ([rowHeight] / 2) at rest that tightens to [ActionPressedCorner] under a finger, exactly the
 * corner-radius animation every context menu in the app already uses — the user asked for these
 * two to read as the same control, not two different ones that happen to share a press gesture.
 *
 * [tint] picks the button's colour from the app's own primary/secondary/tertiary palette rather
 * than leaving every non-hero button on one uniform neutral tone — the row otherwise reads as one
 * grey pill repeated four times with a single accent one buried in the middle of it.
 */
@Composable
internal fun RowScope.ArtistActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    baseWeight: Float,
    iconSize: Dp,
    rowHeight: Dp,
    tint: ActionTint = ActionTint.NEUTRAL
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fast spatial spec, not the default: this sits directly under a finger, and anything
    // leisurely reads as the tap not having registered — same reasoning `ActionButtonGroup`
    // documents for its own press-corner animation.
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val corner by animateDpAsState(
        targetValue = if (pressed) ActionPressedCorner else rowHeight / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "artistActionCorner"
    )
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * PressedGrowth else baseWeight,
        animationSpec = spatial,
        label = "artistActionWeight"
    )
    val (containerColor, contentColor) = tint.colors()

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

/** How much a pressed button takes from its row neighbours — the same figure `ActionButtonGroup` uses. */
private const val PressedGrowth = 1.35f

/** The pressed corner radius — the same figure `ActionButtonGroup`'s own `PressedCorner` uses. */
private val ActionPressedCorner = 14.dp
