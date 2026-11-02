package com.wander.android.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.LyricMatch
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.LyricMatchRow
import com.wander.android.ui.components.rememberShelfEntranceScale

/** How many lyric matches show before the section is expanded. */
private const val COLLAPSED_LYRICS = 5

/**
 * The "Matched in lyrics" section: the first few matches, and a toggle for the rest when there
 * are more than that.
 */
internal fun LazyListScope.lyricMatchesSection(
    matches: List<LyricMatch>,
    query: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPlay: (LyricMatch) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit
) {
    if (matches.isEmpty()) return
    val collapsible = matches.size > COLLAPSED_LYRICS
    val displayed = if (expanded) matches else matches.take(COLLAPSED_LYRICS)

    item(key = "header_lyrics") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_matched_lyrics),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (collapsible) {
                TextButton(
                    onClick = onToggleExpanded,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.search_lyrics_show_less)
                        } else {
                            pluralStringResource(R.plurals.search_lyrics_see_all, matches.size, matches.size)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    itemsIndexed(
        items = displayed,
        key = { _, match -> "lyric_${match.track.id}" },
        contentType = { _, _ -> "lyric_match" }
    ) { index, match ->
        val entranceScale = rememberShelfEntranceScale(index)
        LyricMatchRow(
            match = match,
            searchQuery = query,
            onPlay = { onPlay(match) },
            onLongPress = { onLongPress(match.track) },
            modifier = Modifier.animateItem().scale(entranceScale)
        )
    }
    if (collapsible) {
        item(key = "footer_lyrics_toggle") {
            TextButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem()
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.search_lyrics_show_fewer)
                    } else {
                        val hidden = matches.size - COLLAPSED_LYRICS
                        pluralStringResource(R.plurals.search_lyrics_show_more, hidden, hidden)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** A heading between the result sections. */
internal fun LazyListScope.searchSectionHeader(key: String, @StringRes title: Int) {
    item(key = key, contentType = "section_header") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp)
                .animateItem()
        )
    }
}
