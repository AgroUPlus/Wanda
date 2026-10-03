package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Its total height, so the screens underneath can reserve the space — see [ListenAlongBarHeight]. */
val ListenersBarHeight: Dp = 64.dp

/**
 * The host's side of listen-along: who is following you, above the mini-player.
 *
 * The counterpart of [ListenAlongBar], and deliberately shaped like it — the same slot, the same
 * footprint — but on the tertiary colours, so "you are following someone" and "someone is
 * following you" never read as the same thing at a glance. The way out is right on it: going
 * incognito ends every session following this account. Tapping the rest opens Privacy settings.
 */
@Composable
internal fun ListenersBar(
    listeners: List<String>,
    onGoIncognito: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val first = listeners.firstOrNull() ?: return
    val others = listeners.size - 1
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenPrivacy)
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AvatarGroup(usernames = listeners, size = 28.dp, overlap = 8.dp, maxDisplay = 3)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (others == 0) {
                        stringResource(R.string.together_listeners_bar_single, first)
                    } else {
                        pluralStringResource(R.plurals.together_listeners_bar_more, others, first, others)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
                Text(
                    text = stringResource(R.string.together_listeners_bar_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            FilledTonalButton(
                onClick = onGoIncognito,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                contentPadding = ButtonDefaults.SmallContentPadding
            ) { Text(stringResource(R.string.together_go_incognito)) }
        }
    }
}
