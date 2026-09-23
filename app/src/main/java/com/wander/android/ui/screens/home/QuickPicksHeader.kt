package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The one shelf on Home that gets a fully expressive treatment instead of [SectionTitle] — the
 * lead shelf, worth a title that reads as a small event rather than a list label. Split into its
 * own file rather than inlined in `HomeShelves.kt` so a future shelf that wants the same idea
 * doesn't have to be carved out of a bigger `when` first.
 */
@Composable
internal fun QuickPicksHeader(title: String, onPlayAll: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Stacked rather than run on one line — "QUICK" / "PICKS" reads as a small poster rather
        // than a longer label that happens to be bold. Falls back to a single line for any other
        // one-word title so this doesn't break if the shelf is ever renamed.
        val words = title.uppercase().split(" ", limit = 2)
        words.forEach { word ->
            Text(
                text = word,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_quick_picks_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onPlayAll, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_play_all))
            }
        }
    }
}
