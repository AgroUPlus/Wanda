package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.data.sources.agro.JamTrack

@Composable
internal fun JamSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
internal fun ProposalRow(track: JamTrack, jam: Jam, viewModel: JamViewModel) {
    JamTrackRow(
        track = track,
        subtitle = stringResource(R.string.social_more_go, track.artist, track.stillNeeded),
        jam = jam,
        viewModel = viewModel
    ) {
        if (track.approved) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.social_approved),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            FilledTonalButton(onClick = { viewModel.approve(track.id) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_accept))
            }
        }
    }
}

@Composable
internal fun JamQueueRow(track: JamTrack, jam: Jam, viewModel: JamViewModel) {
    JamTrackRow(
        track = track,
        subtitle = stringResource(R.string.social_added_by, track.artist, track.addedBy),
        jam = jam,
        viewModel = viewModel
    ) {}
}

@Composable
internal fun JamTrackRow(
    track: JamTrack,
    subtitle: String,
    jam: Jam,
    viewModel: JamViewModel,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
        if (jam.isHost || track.addedBy.equals(jam.host, ignoreCase = true)) {
            IconButton(onClick = { viewModel.remove(track.id) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_remove))
            }
        }
    }
}

@Composable
internal fun StartOrJoin(
    onCreate: (JamMode) -> Unit,
    onJoin: (String) -> Unit,
    friendJams: List<FriendJam>,
    onJoinFriendJam: (String) -> Unit,
    error: String?,
    initialCode: String? = null,
    modifier: Modifier = Modifier
) {
    var code by remember(initialCode) { mutableStateOf(initialCode.orEmpty()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.social_one_queue_everyone_room_plays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (friendJams.isNotEmpty()) {
            Text(stringResource(R.string.social_friends_jamming), style = MaterialTheme.typography.titleSmall)
            friendJams.forEach { open ->
                Card(
                    onClick = { onJoinFriendJam(open.id) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.social_s_jam, open.host), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = open.nowPlayingTitle?.let { "Playing $it" }
                                ?: "${open.members.size} in the room",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = { onCreate(JamMode.DEMOCRACY) }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.social_start_jam))
        }

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text(stringResource(R.string.common_join_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { onJoin(code) },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes()
        ) {
            Text(stringResource(R.string.social_join))
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
