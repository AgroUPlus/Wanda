package com.wander.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The badge on the Sync tab's Agro row — the one settings identity that isn't a
 * [com.wander.android.data.model.SourceType] and so doesn't go through
 * [com.wander.android.ui.components.SourceIcon]. Agro is a paired device, not a music source.
 *
 * Takes its colour from the parent category's hue rather than a fixed identity colour, since
 * unlike the three music sources it has no brand colour of its own to be consistent about.
 */
@Composable
internal fun AgroBadge(hue: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BadgeSize)
            .background(hue.copy(alpha = BadgeAlpha), MaterialShapes.SoftBurst.toShape())
    ) {
        Image(
            painter = painterResource(R.drawable.agro_logo),
            contentDescription = null,
            modifier = Modifier.size(GlyphSize)
        )
    }
}

/** Smaller than the hub's badge, so a row inside a page reads as subordinate to the page itself. */
private val BadgeSize = 40.dp
private val GlyphSize = 22.dp
private const val BadgeAlpha = 0.16f
