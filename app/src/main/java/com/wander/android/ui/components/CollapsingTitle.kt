package com.wander.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.min

/**
 * How far into the list has been scrolled, as 0f (at the very top) to 1f (scrolled past
 * [collapseDistance]) — the fraction [CollapsingTitle] shrinks by.
 *
 * Reads only `firstVisibleItemIndex`/`firstVisibleItemScrollOffset`, which change on every scroll
 * tick without the recomposition cost of reading the list's full layout info, and the
 * `derivedStateOf` means the title only recomposes when the *fraction* actually changes, not on
 * every pixel of scroll.
 */
@Composable
fun rememberCollapseFraction(listState: LazyListState, collapseDistance: Dp = 56.dp): State<Float> {
    val collapseDistancePx = with(LocalDensity.current) { collapseDistance.toPx() }
    return remember(listState, collapseDistancePx) {
        derivedStateOf {
            val offsetPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                collapseDistancePx
            }
            min(offsetPx / collapseDistancePx, 1f)
        }
    }
}

/**
 * A title that shrinks into the header as the list under it scrolls, and grows back the moment
 * scrolling returns to the top — the same feel as Android's own Settings app. Lives in the fixed
 * header above the list (see `headerInset()`/`listInset()`), not a `Scaffold` top bar: this app's
 * single outer `Scaffold` already owns the real top inset, so a second one here would risk
 * double-applying it.
 */
@Composable
fun CollapsingTitle(
    text: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    startPadding: Dp = 20.dp
) {
    val expanded = MaterialTheme.typography.headlineLarge
    val collapsed = MaterialTheme.typography.headlineSmall
    Text(
        text = text,
        style = expanded.copy(
            fontSize = lerp(expanded.fontSize, collapsed.fontSize, collapseFraction),
            lineHeight = lerp(expanded.lineHeight, collapsed.lineHeight, collapseFraction)
        ),
        modifier = modifier.padding(
            start = startPadding,
            top = lerp(16.dp, 8.dp, collapseFraction),
            bottom = lerp(12.dp, 8.dp, collapseFraction)
        )
    )
}
