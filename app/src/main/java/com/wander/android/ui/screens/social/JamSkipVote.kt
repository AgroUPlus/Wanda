package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.JamNowPlaying
import com.wander.android.ui.theme.buttonLarge

/**
 * Voting to skip what the room is on — or that you already have — and the way back into sync
 * when this device has drifted from the room.
 */
@Composable
internal fun SkipVote(now: JamNowPlaying, onVoteSkip: () -> Unit) {
    if (now.youSkipped) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = stringResource(R.string.social_voted_skip, now.skipVotes, now.skipsNeeded),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onVoteSkip,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(start = 20.dp, end = 24.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.social_vote_skip),
                style = MaterialTheme.typography.buttonLarge,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        if (now.skipVotes > 0) {
            Text(
                text = stringResource(R.string.jam_skip_votes, now.skipVotes.toInt(), now.skipsNeeded.toInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
internal fun DriftedFromRoom(onResync: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.social_ve_drifted_from_room),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        FilledTonalButton(onClick = onResync, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.social_rejoin))
        }
    }
}
