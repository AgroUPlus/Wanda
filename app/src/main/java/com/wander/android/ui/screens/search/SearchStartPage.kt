package com.wander.android.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset

/**
 * What Search shows under an empty field: the last few searches as chips, then the mood radios.
 *
 * The moods are the same presets as Home's radio button, so a mood is one tap from either place.
 * Each tile starts a radio drawn from every connected source — see `MoodRadioRepository`.
 */
@Composable
internal fun SearchStartPage(
    recentSearches: List<String>,
    moods: List<MoodPreset>,
    playingMoodKey: String?,
    contentPadding: PaddingValues,
    onSearch: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    onPlayMood: (MoodPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        if (recentSearches.isNotEmpty()) {
            recentSearchesSection(recentSearches, onSearch, onRemoveSearch, onClearSearches)
        }

        item(key = "header_moods", contentType = "section_header") {
            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (recentSearches.isEmpty()) 4.dp else 28.dp,
                    bottom = 12.dp
                )
            ) {
                SectionTitle(R.string.search_mood_radio_title)
                Text(
                    text = stringResource(R.string.search_mood_radio_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "mood_grid", contentType = "mood_grid") {
            MoodTileGrid(
                moods = moods,
                playingMoodKey = playingMoodKey,
                onPlayMood = onPlayMood,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/** The "Recent searches" title with its Clear all, then the searches as wrapping chips. */
private fun LazyListScope.recentSearchesSection(
    queries: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    item(key = "header_recent", contentType = "section_header") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp)
        ) {
            SectionTitle(R.string.search_recent_title)
            TextButton(onClick = onClearAll) {
                Text(stringResource(R.string.search_recent_clear_all))
            }
        }
    }
    item(key = "recent_chips", contentType = "recent_chips") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .animateItem()
        ) {
            queries.forEach { query ->
                RecentSearchChip(
                    query = query,
                    onSelect = { onSearch(query) },
                    onRemove = { onRemove(query) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    @StringRes title: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}
