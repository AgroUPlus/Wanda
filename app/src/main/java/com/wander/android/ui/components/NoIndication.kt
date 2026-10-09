package com.wander.android.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * Draws nothing when something is pressed: no ripple. Provided as `LocalIndication` where the press
 * is already answered another way, as it is on the settings pages, whose rows round off and shrink.
 */
@Stable
internal object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}
