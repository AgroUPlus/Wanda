package com.wander.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A tonal container for a set of related rows — settings sections, and anywhere else a screen
 * offers many options that read better grouped than loose in the page background.
 *
 * One shape for the whole group, not a per-row corner split. Material's own grouped lists (unlike
 * iOS's inset style) draw the group as a single surface; that also sidesteps having every caller
 * track which of its children is first/last to get the right corner, which is an easy thing to get
 * wrong once the same pattern is repeated across a dozen call sites. Rows inside supply their own
 * vertical padding already, so no divider is drawn between them — see [SettingsRow].
 */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}
