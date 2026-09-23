package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.MorphingActionButton

/**
 * A heading in the artist list, with its optional "Show all" / "See all". The action morphs rather
 * than swaps — see [MorphingActionButton] — including into a loading indicator while [isBusy].
 */
@Composable
internal fun ArtistSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    actionSelected: Boolean = false,
    isBusy: Boolean = false,
    onAction: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        if (action != null || isBusy) {
            MorphingActionButton(
                label = action.orEmpty(),
                onClick = onAction,
                selected = actionSelected,
                busy = isBusy
            )
        }
    }
}
