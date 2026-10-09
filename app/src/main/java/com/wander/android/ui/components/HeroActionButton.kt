package com.wander.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Which of the theme's own tonal pairs a button draws from — see [HeroActionButton]'s [tint].
 *
 * Every one of these already exists in `WandaLightScheme`/`WandaDarkScheme`
 * ([com.wander.android.ui.theme.WandaLightScheme]): this is a palette choice, not a new colour.
 */
internal enum class HeroActionTint { PRIMARY_SOLID, PRIMARY_CONTAINER, SECONDARY_CONTAINER, TERTIARY_CONTAINER, NEUTRAL }

@Composable
private fun HeroActionTint.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        // The one solid, non-container colour — the hero play button, the single control the
        // page exists for. Everything else stays on a tonal container, including a toggle that's
        // switched on: a solid `primary` pill there would fight the hero for the same emphasis.
        HeroActionTint.PRIMARY_SOLID -> scheme.primary to scheme.onPrimary
        HeroActionTint.PRIMARY_CONTAINER -> scheme.primaryContainer to scheme.onPrimaryContainer
        HeroActionTint.SECONDARY_CONTAINER -> scheme.secondaryContainer to scheme.onSecondaryContainer
        HeroActionTint.TERTIARY_CONTAINER -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        HeroActionTint.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurface
    }
}

/**
 * One button in a detail page's hero rows — the artist, album and playlist pages all use it.
 *
 * Each button is `Modifier.weight(...)` rather than a fixed size, the identical mechanism
 * `ActionButtonGroup` already uses for every context menu in the app: a button's *own* press state
 * animates *its own* weight up, and the Row's normal weighted layout does the "neighbours give way"
 * part on its own — nothing here tracks sibling buttons.
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
internal fun RowScope.HeroActionButton(
    onClick: () -> Unit,
    /** Read out for an icon-only button; null when [label] already says what the button does. */
    contentDescription: String?,
    icon: ImageVector?,
    baseWeight: Float,
    iconSize: Dp,
    rowHeight: Dp,
    tint: HeroActionTint = HeroActionTint.NEUTRAL,
    /** Drawn after the icon, for a button whose action an icon alone would not name — see `ProfileActions`. */
    label: String? = null
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
        label = "heroActionCorner"
    )
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * PressedGrowth else baseWeight,
        animationSpec = spatial,
        label = "heroActionWeight"
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
            // Pressed from the moment a finger lands, not after a click's own short delay.
            .instantPress(interaction)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = LabelPadding)
        ) {
            icon?.let { Icon(it, contentDescription = contentDescription, modifier = Modifier.size(iconSize)) }
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = LabelSize),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (icon != null) IconLabelGap else 0.dp)
                )
            }
        }
    }
}

private val LabelPadding = 12.dp
private val IconLabelGap = 8.dp
private val LabelSize = 16.sp

/** How much a pressed button takes from its row neighbours — the same figure `ActionButtonGroup` uses. */
private const val PressedGrowth = 1.35f

/** The pressed corner radius — the same figure `ActionButtonGroup`'s own `PressedCorner` uses. */
private val ActionPressedCorner = 14.dp

/**
 * A full-width row of [HeroActionButton]s, spanning exactly what the song list below it does — the
 * same 16 dp inset `groupedListItem` uses. Shared with the pages' skeletons, which mirror it so
 * nothing resizes when the real page lands.
 */
@Composable
internal fun HeroActionRow(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HeroButtonGap),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = HeroRowInset)
            .then(modifier),
        content = content
    )
}

/** Matches `groupedListItem`'s own default inset — the song list below reads as the same width. */
private val HeroRowInset = 16.dp
private val HeroButtonGap = 10.dp

/** The transport row: play and the controls that start playback some other way. */
internal val HeroTransportRowHeight = 104.dp

/** The row under it: share, follow, convert, download. */
internal val HeroSecondaryRowHeight = 80.dp

internal val HeroPlayIconSize = 32.dp
internal val HeroActionIconSize = 26.dp

/**
 * The play button's share of the transport row before any press. Wider than its neighbours, not
 * just present among them: the one control the page exists for still reads as the biggest thing in
 * the row, the same way `WideWeight` gives a context menu's primary action more room.
 */
internal const val HeroPlayWeight = 1.6f
