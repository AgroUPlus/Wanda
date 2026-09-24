package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

private val PERIODS = listOf("WEEK", "MONTH", "YEAR", "ALL")

/**
 * Circle Tab — Material 3 Expressive recap, taste compatibility, and friend activity feed.
 */
@Composable
internal fun CircleScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: CircleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.social_circle),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.social_circle_s_rhythm_recap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ButtonGroup(
            overflowIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            PERIODS.forEach { period ->
                toggleableItem(
                    checked = state.period == period,
                    label = period.lowercase().replaceFirstChar { it.uppercase() },
                    onCheckedChange = { viewModel.setPeriod(period) },
                    weight = 1f
                )
            }
        }

        if (state.feed.isEmpty() && state.recap == null && !state.loading) {
            EmptyState(
                title = stringResource(R.string.social_nothing_circle_yet),
                message = stringResource(R.string.social_friends_appear_here_once_they)
            )
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            state.recap?.let { recap ->
                item(key = "recap_anthem") {
                    recap.anthem?.let { AnthemHeroCard(it) }
                }

                item(key = "recap_trendsetter") {
                    recap.trendsetter?.let { TrendsetterCard(it) }
                }

                if (recap.topArtists.isNotEmpty() || recap.topTracks.isNotEmpty()) {
                    item(key = "recap_charts") {
                        CircleLeaderboards(
                            topArtists = recap.topArtists,
                            topTracks = recap.topTracks
                        )
                    }
                }

                if (recap.matrix.isNotEmpty()) {
                    item(key = "recap_matrix") {
                        TasteMatrixSection(recap.matrix)
                    }
                }
            }

            if (state.feed.isNotEmpty()) {
                item(key = "feed_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.social_lately_circle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(count = state.feed.size, key = { index -> "feed_$index" }) { index ->
                    FeedItemCard(state.feed[index])
                }
            }
        }
    }
}
