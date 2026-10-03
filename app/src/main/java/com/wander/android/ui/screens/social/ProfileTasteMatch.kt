package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.AgroTasteMatch
import com.wander.android.data.sources.agro.FriendState

/**
 * How much the two of you overlap, and the artists you share.
 *
 * Where the answer is missing the section says why: an empty score and a score someone chose not to
 * share look identical, and only one of them is worth explaining.
 */
internal fun LazyListScope.tasteMatchSection(profile: AgroProfile, match: AgroTasteMatch?) {
    item(key = "taste") {
        Column {
            SectionTitle(stringResource(R.string.social_taste_match), top = 32.dp)
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                when {
                    match != null -> {
                        Text(
                            text = stringResource(R.string.social_percent_in_common, match.score.toString()),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        LinearWavyProgressIndicator(
                            progress = { match.score / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    profile.friendState != FriendState.ACCEPTED -> Explanation(
                        stringResource(R.string.common_will_see_once_friends)
                    )
                    !profile.showStats -> Explanation(
                        stringResource(R.string.social_keeps_stats_private, profile.name)
                    )
                    else -> Explanation(stringResource(R.string.social_not_enough_listening_between_yet))
                }
            }
        }
    }

    if (match != null && match.sharedArtists.isNotEmpty()) {
        item(key = "shared_header") {
            Text(
                text = stringResource(R.string.social_artists_both_play),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)
            )
        }
        items(match.sharedArtists, key = { "shared_" + it.name }) { entry ->
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun Explanation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
