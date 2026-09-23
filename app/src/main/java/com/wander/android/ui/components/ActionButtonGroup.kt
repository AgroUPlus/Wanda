package com.wander.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How loudly an action is drawn — and so where [ActionButtonGroup] places it. */
enum class ActionEmphasis {
    /** The reason the menu was opened. Big, filled, first row. */
    PRIMARY,

    /** Worth the first row too, but second to [PRIMARY]. */
    SECONDARY,

    /** Everything else with a label. */
    TONAL,

    /** A quick toggle that reads from its icon alone — like, share. Grouped into one short row. */
    ICON,

    /** Removes or deletes something. Always last, full width, in the error colour. */
    DANGER
}

@Immutable
class MenuAction(
    val icon: ImageVector,
    val label: String,
    val emphasis: ActionEmphasis = ActionEmphasis.TONAL,
    /**
     * Non-null for an on/off setting — like, radio. Toggles are drawn apart from buttons: a
     * rounded square rather than a pill, outlined while off and filled while on, so their state
     * reads at a glance and the label never has to say "(active)".
     */
    val selected: Boolean? = null,
    val onClick: () -> Unit
)

/**
 * The body of every context menu: buttons of different sizes and colours in rows rather than a
 * flat list, M3 Expressive button-group style. Pressing a button widens it and its row
 * neighbours give way, on a spring, and its corners tighten — the whole row answers the finger.
 *
 * Callers hand over a flat list tagged with [ActionEmphasis]; the arrangement is decided here so
 * every menu in the app has the same shape: primary row, one row of icon toggles, labelled rows two
 * to a row in alternating wide/narrow pairs, destructive actions at the foot.
 */
@Composable
fun ActionButtonGroup(actions: List<MenuAction>, modifier: Modifier = Modifier) {
    val hero = actions.filter {
        it.emphasis == ActionEmphasis.PRIMARY || it.emphasis == ActionEmphasis.SECONDARY
    }
    val tonal = actions.filter { it.emphasis == ActionEmphasis.TONAL }
    val icons = actions.filter { it.emphasis == ActionEmphasis.ICON }
    val danger = actions.filter { it.emphasis == ActionEmphasis.DANGER }

    Column(
        verticalArrangement = Arrangement.spacedBy(RowGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Even split, not wideFirst: an uneven hero row squeezed "Add to queue" enough to ellipsize.
        hero.chunked(2).forEach { row -> ActionRow(row, HeroHeight, wideFirst = null) }
        if (icons.isNotEmpty()) {
            icons.chunked(4).forEach { row -> ActionRow(row, RowHeight, wideFirst = null) }
        }
        tonal.chunked(2).forEachIndexed { index, row ->
            ActionRow(row, RowHeight, wideFirst = index % 2 == 0)
        }
        danger.forEach { ActionRow(listOf(it), RowHeight, wideFirst = null) }
    }
}

/**
 * One row. [wideFirst] gives a pair its uneven widths — alternating row to row is what keeps the
 * grid from reading as a table — and null keeps the row even.
 */
@Composable
private fun ActionRow(row: List<MenuAction>, height: Dp, wideFirst: Boolean?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        row.forEachIndexed { index, action ->
            val baseWeight = when {
                wideFirst == null || row.size == 1 -> 1f
                (index == 0) == wideFirst -> WideWeight
                else -> 1f
            }
            ActionButton(action, baseWeight, isHero = height == HeroHeight)
        }
    }
}

@Composable
private fun RowScope.ActionButton(action: MenuAction, baseWeight: Float, isHero: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * PressedGrowth else baseWeight,
        animationSpec = spatial,
        label = "actionWeight"
    )
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> PressedCorner
            action.selected != null -> ToggleCorner
            else -> height(isHero) / 2
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "actionCorner"
    )
    val (targetContainer, targetContent) = colorsFor(action.emphasis, action.selected)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(targetContainer, effects, label = "actionContainer")
    val content by animateColorAsState(targetContent, effects, label = "actionContent")

    val outline = if (action.selected == false) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    } else {
        null
    }

    Surface(
        onClick = action.onClick,
        border = outline,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (action.emphasis == ActionEmphasis.ICON) {
                Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(24.dp))
            } else {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(if (isHero) 24.dp else 20.dp))
                val style = if (isHero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge
                // One line, always: a label that does not fit steps its size down instead of
                // wrapping onto a second line the button has no room for, or losing its end.
                BasicText(
                    text = action.label,
                    style = style.copy(color = LocalContentColor.current),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = MinLabelSize, maxFontSize = style.fontSize),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * The menu's whole palette — deliberately few colours, each with one meaning: primary for the one
 * action the menu is for, a single neutral surface for every other button, primaryContainer for a
 * toggle that is on (off is just an outline), and the error colour for removing things.
 */
@Composable
private fun colorsFor(emphasis: ActionEmphasis, selected: Boolean?): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    if (selected != null) {
        return if (selected) scheme.primaryContainer to scheme.onPrimaryContainer
        else Color.Transparent to scheme.onSurfaceVariant
    }
    return when (emphasis) {
        ActionEmphasis.PRIMARY -> scheme.primary to scheme.onPrimary
        ActionEmphasis.SECONDARY, ActionEmphasis.TONAL, ActionEmphasis.ICON ->
            scheme.surfaceContainerHighest to scheme.onSurface
        ActionEmphasis.DANGER -> scheme.errorContainer to scheme.onErrorContainer
    }
}

private fun height(isHero: Boolean): Dp = if (isHero) HeroHeight else RowHeight

private val HeroHeight = 72.dp
private val RowHeight = 56.dp
private val RowGap = 8.dp
private val ButtonGap = 8.dp
private val PressedCorner = 14.dp
private val ToggleCorner = 16.dp
private val MinLabelSize = 10.sp

/** The wider of an uneven pair. */
private const val WideWeight = 1.5f

/** How much a pressed button takes from its neighbours. */
private const val PressedGrowth = 1.35f
