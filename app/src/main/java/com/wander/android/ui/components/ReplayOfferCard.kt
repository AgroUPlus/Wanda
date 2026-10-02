package com.wander.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * "Your year is ready" — the banner that opens Agro Replay in November and December.
 *
 * Shares the bottom slot with the sync and handoff offers rather than inventing a second place for
 * unsolicited cards to appear. Three ways out, because they mean different things: Later hides it
 * until the app next starts, "Don't show again" sets this year aside (the year watermark in
 * `SecureStorage`), and both are safe because the recap stays in Settings › About, which the banner
 * says so nobody has to wonder where it went.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReplayOfferCard(
    year: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onNeverShow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ReplayEmblem()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.replay_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                    Text(
                        text = stringResource(R.string.replay_offer_title, year),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.replay_offer_subtitle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = stringResource(R.string.replay_offer_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer.copy(alpha = HintAlpha)
            )
            // Wraps on a narrow phone or with large text rather than squeezing three labels.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.replay_offer_open))
                }
                OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.replay_offer_later))
                }
                TextButton(onClick = onNeverShow, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.replay_offer_never))
                }
            }
        }
    }
}

/** A burst that turns slowly behind the confetti, still when the system asks for less motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplayEmblem() {
    val reduced = LocalReducedMotion.current
    val rotation by rememberInfiniteTransition(label = "replayEmblem").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(TurnMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "replayEmblemTurn"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EmblemSize)) {
        Box(
            modifier = Modifier
                .size(EmblemSize)
                .graphicsLayer { rotationZ = if (reduced) 0f else rotation }
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.primary)
        )
        Icon(
            imageVector = Icons.Rounded.Celebration,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(IconSize)
        )
    }
}

private val EmblemSize = 64.dp
private val IconSize = 30.dp
private const val TurnMillis = 12_000
private const val HintAlpha = 0.8f
