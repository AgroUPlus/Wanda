package com.wander.android.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.WandaSheet
import com.wander.android.ui.components.pressShrink
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressMorph
import com.wander.android.ui.components.trackPress
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One thing the user can show or hide. */
internal class Choice<T>(val value: T, val label: String, val icon: ImageVector)

/**
 * Pick which of [all] to show and put the shown ones in order. Shown choices come first; hold one
 * to move it, tap one to show or hide it: a shown choice is drawn normally, a hidden one greyed out. At most [maxSelected] and at least [minSelected] stay shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> OrderedChoiceSheet(
    title: String,
    hint: String,
    all: List<Choice<T>>,
    selected: List<T>,
    minSelected: Int,
    maxSelected: Int,
    onChange: (List<T>) -> Unit,
    onDismiss: () -> Unit
) {
    // Reordered here at once and saved as it goes; the saved copy arrives a beat later and would
    // otherwise pull a dragged row back a slot.
    var shown by remember { mutableStateOf(selected) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = shown.indexOf(from.key)
        val toIndex = shown.indexOf(to.key)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        shown = shown.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        onChange(shown)
    }
    val dragging = reorder.isAnyItemDragging
    LaunchedEffect(selected, dragging) { if (!dragging) shown = selected }

    val rows = shown + all.map { it.value }.filter { it !in shown }
    val choices = all.associateBy { it.value }
    val haptics = rememberHaptics()

    WandaSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedItemGap)
        ) {
            item(key = "header") {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
                )
            }
            itemsIndexed(rows, key = { _, value -> value }) { index, value ->
                val isShown = value in shown
                val canToggle = if (isShown) shown.size > minSelected else shown.size < maxSelected
                ReorderableItem(reorder, key = value, enabled = isShown) { isDragging ->
                    ChoiceRow(
                        choice = choices.getValue(value),
                        shown = isShown,
                        enabled = canToggle,
                        lifted = isDragging,
                        index = index,
                        count = rows.size,
                        onToggle = {
                            shown = if (isShown) shown - value else shown + value
                            onChange(shown)
                        },
                        modifier = Modifier.longPressDraggableHandle(
                            enabled = isShown,
                            onDragStarted = { haptics.heldDown() },
                            onDragStopped = { haptics.settled() }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: Choice<*>,
    shown: Boolean,
    enabled: Boolean,
    lifted: Boolean,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val morph = rememberPressMorph()
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        when {
            lifted -> MaterialTheme.colorScheme.surfaceContainerHighest
            shown -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        motion.fastEffectsSpec(),
        label = "choiceRow"
    )
    // A hidden choice is greyed out rather than ticked off: it reads as present but switched off.
    val strength by animateFloatAsState(if (shown) 1f else HiddenStrength, motion.fastEffectsSpec(), label = "choiceStrength")
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = morph.shape(index, count),
        color = container,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth().trackPress(morph)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .pressShrink(morph)
                .alpha(strength)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Icon(
                    choice.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Text(choice.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

private const val HiddenStrength = 0.45f
