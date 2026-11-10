package com.wander.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * "Recently added" and "Most played", side by side as one connected pair: round outer corners,
 * tight inner ones, the way M3 Expressive groups siblings. Each tile plays its slice at once.
 */
@Composable
internal fun SearchShortcutTiles(
    shortcuts: List<SearchShortcut>,
    onPlay: (SearchShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        shortcuts.forEachIndexed { index, shortcut ->
            ShortcutTile(
                shortcut = shortcut,
                shape = pairShape(index, shortcuts.lastIndex),
                onClick = { onPlay(shortcut) },
                modifier = Modifier.weight(1f)
            )
        }
        // A lone shortcut keeps its half width, matching the mood grid below it.
        if (shortcuts.size == 1) Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutTile(
    shortcut: SearchShortcut,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(shortcut.title)
    val description = stringResource(R.string.search_shortcut_play, title)
    val (badge, icon) = when (shortcut) {
        SearchShortcut.RECENTLY_ADDED -> MaterialShapes.Cookie9Sided to Icons.Rounded.LibraryAdd
        SearchShortcut.MOST_PLAYED -> MaterialShapes.Sunny to Icons.AutoMirrored.Rounded.TrendingUp
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.surfaceContainerHigh,
        modifier = modifier
            .height(72.dp)
            .semantics { contentDescription = description }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(colors.primary, badge.toShape())
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(shortcut.hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val SearchShortcut.title: Int
    get() = when (this) {
        SearchShortcut.RECENTLY_ADDED -> R.string.search_shortcut_recently_added
        SearchShortcut.MOST_PLAYED -> R.string.search_shortcut_most_played
    }

private val SearchShortcut.hint: Int
    get() = when (this) {
        SearchShortcut.RECENTLY_ADDED -> R.string.search_shortcut_recently_added_hint
        SearchShortcut.MOST_PLAYED -> R.string.search_shortcut_most_played_hint
    }

private fun pairShape(index: Int, last: Int): RoundedCornerShape {
    val start = if (index == 0) Outer else Inner
    val end = if (index == last) Outer else Inner
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

private val Outer = 26.dp
private val Inner = 8.dp
