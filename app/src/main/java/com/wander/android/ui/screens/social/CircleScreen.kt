package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.theme.screenTitle

private val PERIODS = listOf("WEEK", "MONTH", "YEAR", "ALL")

/**
 * The circle — you and the friends who share their statistics — as a recap: its anthem, who got
 * there first, what it plays, and how alike its members are.
 */
@Composable
internal fun CircleScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: CircleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CircleTopBar(contentPadding, members = state.recap?.members.orEmpty(), onBack = onBack)

        ConnectedToggleGroup(
            options = PERIODS,
            selected = state.period,
            label = { stringResource(periodLabel(it)) },
            onSelect = viewModel::setPeriod,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )

        if (state.feed.isEmpty() && state.recap == null && !state.loading) {
            EmptyState(
                title = stringResource(R.string.social_nothing_circle_yet),
                message = stringResource(R.string.social_friends_appear_here_once_they)
            )
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            state.recap?.let { recap ->
                recap.anthem?.let { anthem -> item(key = "anthem") { AnthemHeroCard(anthem) } }
                recap.trendsetter?.let { trendsetter -> item(key = "trendsetter") { TrendsetterCard(trendsetter) } }
                if (recap.topArtists.isNotEmpty() || recap.topTracks.isNotEmpty()) {
                    item(key = "charts") { CircleCharts(recap.topArtists, recap.topTracks) }
                }
                if (recap.matrix.isNotEmpty()) tasteCompatibilitySection(recap.matrix)
            }

            if (state.feed.isNotEmpty()) {
                item(key = "feed_header") { SectionTitle(stringResource(R.string.social_lately_circle)) }
                items(count = state.feed.size, key = { index -> "feed_$index" }) { index ->
                    FeedItemCard(state.feed[index], modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CircleTopBar(contentPadding: PaddingValues, members: List<String>, onBack: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding.headerInset())
            .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
            .fillMaxWidth()
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.social_circle),
            style = MaterialTheme.typography.screenTitle,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            members.take(MAX_FACES).forEach { CuteAvatar(seed = it, size = 32.dp, shape = PersonShape) }
        }
    }
}

private fun periodLabel(period: String): Int = when (period) {
    "WEEK" -> R.string.circle_period_week
    "MONTH" -> R.string.circle_period_month
    "YEAR" -> R.string.circle_period_year
    else -> R.string.circle_period_all
}

private const val MAX_FACES = 3
