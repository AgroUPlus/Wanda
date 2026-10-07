package com.wander.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single-choice switch drawn as connected toggle buttons: tight inner corners while unchecked,
 * morphing to a full pill and gaining a check when chosen.
 *
 * Every switch on the social and statistics screens is one of these, so choosing between two lists
 * looks and moves the same everywhere.
 *
 * With [equalWidth] the buttons split the row and shrink their labels to fit. Without it each takes
 * the width of its own content, for a row that may be wider than the screen and is scrolled by its
 * caller — the source filter, whose length depends on how many backends are connected.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    uncheckedContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    checkIconGap: Dp = 6.dp,
    equalWidth: Boolean = true,
    enabled: Boolean = true,
    /** Drawn before the label while the option is not chosen; the check takes its place when it is. */
    leadingIcon: (@Composable (T) -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = if (equalWidth) modifier.fillMaxWidth() else modifier
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = uncheckedContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = (if (equalWidth) Modifier.weight(1f) else Modifier)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton }
            ) {
                AnimatedVisibility(visible = checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = checkIconGap).size(18.dp)
                    )
                }
                if (leadingIcon != null && !checked) {
                    Box(Modifier.padding(end = checkIconGap)) { leadingIcon(option) }
                }
                // One line: a label too long for its share of the row steps down instead of being cut.
                val style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                BasicText(
                    text = label(option),
                    style = style.copy(color = LocalContentColor.current),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = if (equalWidth) {
                        TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize)
                    } else {
                        null
                    }
                )
            }
        }
    }
}
