package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.wander.android.R

/**
 * The bar a detail page's title collapses into once its hero scrolls away — see
 * [CollapsingTitleState] for the hand-off. The back button is always there; the bar's surface
 * and the play button arrive with the title, in step with [CollapsingTitleState.fraction].
 *
 * [heroTitleStyle] and [heroTitleMaxLines] must match the hero's own title so the travelling copy
 * starts out indistinguishable from the text it replaces.
 */
@Composable
fun CompactHeroTopBar(
    titleState: CollapsingTitleState,
    onBack: () -> Unit,
    title: String,
    heroTitleStyle: TextStyle,
    onPlay: () -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
    heroTitleMaxLines: Int = 3
) {
    val collapseDistancePx = with(LocalDensity.current) { CollapseDistance.toPx() }
    SideEffect { titleState.collapseDistancePx = collapseDistancePx }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + BarHeight)
                .graphicsLayer { alpha = titleState.fraction }
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(BarHeight)
                // 12 dp plus the buttons' own 4 dp touch-target margin puts both the back button
                // and the play button 16 dp from their edges — the same inset as the lists below.
                .padding(horizontal = 12.dp)
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .onGloballyPositioned {
                        titleState.slotBounds = Rect(it.positionInRoot(), it.size.toSize())
                    }
            ) {
                TravellingTitle(
                    state = titleState,
                    title = title,
                    heroStyle = heroTitleStyle,
                    heroMaxLines = heroTitleMaxLines,
                    barStyle = MaterialTheme.typography.titleLarge
                )
            }

            FilledIconButton(
                onClick = onPlay,
                enabled = titleState.fraction > 0.5f,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.graphicsLayer {
                    val t = titleState.fraction
                    alpha = t
                    scaleX = lerp(0.6f, 1f, t)
                    scaleY = lerp(0.6f, 1f, t)
                }
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play)
                )
            }
        }
    }
}

/**
 * Two copies of the title sharing one moving centre: the hero-styled copy shrinking as it goes and
 * the bar-styled copy growing into place, cross-fading over the middle of the trip so the
 * wrap and weight change never shows as a jump. Both are placed with their centre on the docked
 * position and translated from there, so the transform alone carries them.
 */
@Composable
private fun TravellingTitle(
    state: CollapsingTitleState,
    title: String,
    heroStyle: TextStyle,
    heroMaxLines: Int,
    barStyle: TextStyle
) {
    val sizeRatio = barStyle.fontSize.value / heroStyle.fontSize.value
    // Where the docked copy's centre ends up depends on its own measured width, which the hero
    // copy has to aim for too.
    var dockedWidth by remember { mutableFloatStateOf(0f) }

    // The docked copy: start-aligned in the slot, one line.
    Text(
        text = title,
        style = barStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .onSizeChanged { dockedWidth = it.width.toFloat() }
            .graphicsLayer {
                val t = state.fraction
                val slot = state.slotBounds
                val hero = state.heroTitleBounds
                alpha = crossFade(t)
                if (slot != null && t < 1f) {
                    val dockedX = slot.left + size.width / 2f
                    val heroX = hero?.center?.x ?: dockedX
                    translationX = lerp(heroX, dockedX, t) - dockedX
                    translationY = state.travellingCenterY() - slot.center.y
                    val scale = lerp(1f / sizeRatio, 1f, t)
                    scaleX = scale
                    scaleY = scale
                }
            }
    )

    // The hero's copy, laid out at the hero title's own width so it wraps exactly as it did there.
    Text(
        text = title,
        style = heroStyle,
        textAlign = TextAlign.Center,
        maxLines = heroMaxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .layout { measurable, constraints ->
                val heroWidth = state.heroTitleBounds?.width?.toInt() ?: constraints.maxWidth
                val placeable = measurable.measure(Constraints(maxWidth = heroWidth.coerceAtLeast(0)))
                layout(constraints.minWidth, constraints.minHeight) {
                    val slot = state.slotBounds
                    val slotHeight = slot?.height ?: 0f
                    // Centred on the slot's vertical middle, left edge on the slot's left edge;
                    // `graphicsLayer` below moves it from there.
                    placeable.place(0, ((slotHeight - placeable.height) / 2f).toInt())
                }
            }
            .graphicsLayer {
                val t = state.fraction
                val slot = state.slotBounds ?: return@graphicsLayer
                val hero = state.heroTitleBounds
                alpha = if (t <= 0f || t >= 1f) 0f else 1f - crossFade(t)
                transformOrigin = TransformOrigin.Center
                val placedCenterX = slot.left + size.width / 2f
                val dockedX = slot.left + dockedWidth / 2f
                val heroX = hero?.center?.x ?: dockedX
                translationX = lerp(heroX, dockedX, t) - placedCenterX
                translationY = state.travellingCenterY() - slot.center.y
                val scale = lerp(1f, sizeRatio, t)
                scaleX = scale
                scaleY = scale
            }
    )
}

/** The two copies trade places over the middle of the trip, overlapping so it never dips. */
private fun crossFade(t: Float): Float = ((t - 0.3f) / 0.4f).coerceIn(0f, 1f)

private val BarHeight = 56.dp

/** How far above the bar the title starts sliding into it. */
private val CollapseDistance = 120.dp
