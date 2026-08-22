package com.wander.android.ui.screens.social

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The two places you go from Friends, as a pair of tiles rather than a stack of full-width cards.
 *
 * Three stacked cards pushed the people — the actual subject of the screen — below the fold. Side
 * by side these take one band instead of three, and being coloured rather than plain lets them read
 * as *destinations* at a glance instead of as more list rows.
 */
@Composable
internal fun SocialTiles(
    jamSubtitle: String?,
    circleSubtitle: String,
    onOpenJam: () -> Unit,
    onOpenCircle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        SocialTile(
            icon = Icons.Rounded.GraphicEq,
            title = "Jam",
            subtitle = jamSubtitle ?: "Start one",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            // Only a live jam pulses. An idle tile that breathed would be decoration; this is the
            // one thing on the screen that other people are in right now.
            live = jamSubtitle != null,
            onClick = onOpenJam,
            modifier = Modifier.weight(1f)
        )
        SocialTile(
            icon = Icons.Rounded.AutoAwesome,
            title = "Circle",
            subtitle = circleSubtitle,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            live = false,
            onClick = onOpenCircle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SocialTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    live: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A slow breath rather than a blink: this sits on screen for as long as the jam lasts, and
    // anything sharper would be something to get away from rather than a sign of life.
    val pulse by rememberInfiniteTransition(label = "tile-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "tile-pulse-alpha"
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.height(112.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                if (live) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(8.dp)
                            .alpha(pulse)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The inbox, as a header action rather than a card.
 *
 * It is a place you visit when something arrives, and the badge is what tells you something has —
 * so it earns an icon and a count, not a row of its own competing with the people below it.
 */
@Composable
internal fun InboxAction(unread: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (unread > 0) {
                Badge { Text(if (unread > 99) "99+" else unread.toString()) }
            }
        }
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
            Icon(Icons.Rounded.MoveToInbox, contentDescription = "Inbox")
        }
    }
}
