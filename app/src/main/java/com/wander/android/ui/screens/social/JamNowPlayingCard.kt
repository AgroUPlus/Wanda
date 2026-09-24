package com.wander.android.ui.screens.social

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.Artwork
import kotlinx.coroutines.delay

/**
 * What the room is hearing, with a progress bar synchronized to server time.
 */
@Composable
internal fun JamNowPlayingCard(
    jam: Jam,
    unresolvable: String?,
    outOfSync: Boolean,
    viewModel: JamViewModel
) {
    val now = jam.nowPlaying

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (now == null) "BETWEEN TRACKS" else "EVERYONE IS HEARING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))

            if (now == null) {
                Text(
                    text = stringResource(R.string.social_nothing_playing_yet),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    url = now.artworkUrl,
                    contentDescription = null,
                    sizeDp = 72.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = now.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = now.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val progress by produceState(0f, now.trackId, now.positionMs, now.durationMs) {
                if (now.durationMs <= 0L) {
                    value = 0f
                    return@produceState
                }
                val base = now.positionMs
                val startedAt = SystemClock.elapsedRealtime()
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    value = ((base + elapsed).toFloat() / now.durationMs).coerceIn(0f, 1f)
                    if (value >= 1f) break
                    delay(500)
                }
            }
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                if (now.youSkipped) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.social_voted_skip, now.skipVotes, now.skipsNeeded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    FilledTonalButton(onClick = viewModel::voteSkip, shapes = ButtonDefaults.shapes()) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(stringResource(R.string.social_vote_skip))
                    }
                    if (now.skipVotes > 0) {
                        Text(
                            text = "  ${now.skipVotes}/${now.skipsNeeded}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            AnimatedVisibility(visible = outOfSync && unresolvable == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.social_ve_drifted_from_room),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = viewModel::resync, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.social_rejoin))
                    }
                }
            }

            AnimatedVisibility(visible = unresolvable != null) {
                Text(
                    text = "You don't have “${unresolvable.orEmpty()}” — the room is still playing it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
