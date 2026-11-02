package com.wander.android.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

/**
 * The smallest a target on the strip may be: Material's and the accessibility guidelines' 48dp.
 */
internal val MiniTouchTargetSize = 48.dp

/**
 * Makes the box this is applied to — at least [MiniTouchTargetSize] across — the button that the
 * 40dp control inside it stands for.
 *
 * The strip's buttons are drawn at 40dp and cannot take the usual minimum-size wrapper, which
 * places a 40dp button as if it were 48 and shifts its glyph off the progress ring's centre (see
 * `MiniPlayer`). So the margin round each one is a target of its own: a tap there does what the
 * button does, through the same [interactionSource] so the press still shows, and it is this node,
 * at full size and with [label], that TalkBack and Switch Access see. The control inside keeps its
 * own click for taps on it but is hidden from them with [hiddenFromAccessibility], so it is not
 * announced twice.
 */
internal fun Modifier.miniTouchTarget(
    label: String,
    interactionSource: MutableInteractionSource,
    action: () -> Unit
): Modifier = this
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Button,
        onClick = action
    )
    .clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
        onClick { action(); true }
    }

/** For the visible 40dp control inside a [miniTouchTarget], which already speaks for it. */
internal fun Modifier.hiddenFromAccessibility(): Modifier = clearAndSetSemantics { }
