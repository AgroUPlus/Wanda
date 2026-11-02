package com.wander.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow

private const val MIN_ITEMS = 60
private const val HIDE_DELAY_MS = 1_500L
private val ThumbTouch = 56.dp

/**
 * A draggable scrollbar thumb for a long lazy list or grid, with a letter bubble while dragging.
 *
 * Driven by indices rather than by pixels: a list's rows are only measured once composed, so
 * neither a total height nor an offset into it exists for a ten-thousand-row library. The thumb
 * sits at [firstVisibleItem] out of [totalItems], and dragging it asks [onJumpTo] for an item
 * index — `scrollToItem` on a lazy state, which a Paging list with placeholders answers by loading
 * the page that index falls in.
 *
 * Only the thumb takes touches, and only while it shows (during a scroll and for a moment after):
 * a strip down the whole edge would swallow the like buttons at the end of every row.
 *
 * Place it in a `Box` over the list, aligned to the end, the height of the list's viewport.
 *
 * @param labelFor the bubble's text for an item, or null for an order with no letters to show.
 */
@Composable
fun FastScroller(
    totalItems: Int,
    firstVisibleItem: () -> Int,
    isScrollInProgress: () -> Boolean,
    onJumpTo: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelFor: ((Int) -> String?)? = null
) {
    if (totalItems < MIN_ITEMS) return
    val density = LocalDensity.current
    val thumbPx = with(density) { ThumbTouch.toPx() }
    var trackHeight by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableIntStateOf(0) }
    var recentlyScrolled by remember { mutableStateOf(false) }
    val currentTotal by rememberUpdatedState(totalItems)
    val currentJump by rememberUpdatedState(onJumpTo)
    val currentFirst by rememberUpdatedState(firstVisibleItem)
    val currentScrolling by rememberUpdatedState(isScrollInProgress)
    // Conflated: a fast drag asks for many indices a frame, and only the newest one matters.
    val jumps = remember { Channel<Int>(Channel.CONFLATED) }

    LaunchedEffect(jumps) { jumps.consumeAsFlow().collect { currentJump(it) } }
    LaunchedEffect(Unit) {
        snapshotFlow { currentScrolling() }.collectLatest { scrolling ->
            if (scrolling) {
                recentlyScrolled = true
            } else {
                delay(HIDE_DELAY_MS)
                recentlyScrolled = false
            }
        }
    }

    fun fractionNow(): Float =
        if (dragging) dragFraction
        else (currentFirst().toFloat() / (currentTotal - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { trackHeight = it.height }
    ) {
        AnimatedVisibility(
            visible = dragging || recentlyScrolled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Read in the layout phase, so a scroll moves the thumb without recomposing it.
                .offset { IntOffset(0, (fractionNow() * (trackHeight - thumbPx).coerceAtLeast(0f)).roundToInt()) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val label = if (dragging) labelFor?.invoke(target) else null
                if (label != null) LetterBubble(label)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 28.dp, height = ThumbTouch)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = fractionNow()
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val range = (trackHeight - thumbPx).coerceAtLeast(1f)
                                    dragFraction = (dragFraction + dragAmount / range).coerceIn(0f, 1f)
                                    val index = (dragFraction * (currentTotal - 1)).roundToInt()
                                    if (index != target) {
                                        target = index
                                        jumps.trySend(index)
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (dragging) 8.dp else 6.dp, height = 44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterBubble(label: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ThumbTouch)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 6.dp)
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * The letter an alphabetical list files [name] under: its first letter or digit, upper-cased, or
 * `#` for a name that starts with neither.
 */
fun fastScrollLetter(name: String?): String? {
    val first = name?.trimStart()?.firstOrNull() ?: return null
    return if (first.isLetterOrDigit()) first.uppercaseChar().toString() else "#"
}
