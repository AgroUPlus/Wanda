package com.wander.android.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.data.sources.agro.JamTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape
import com.wander.android.ui.theme.listSupporting
import com.wander.android.ui.theme.listTitle
import com.wander.android.ui.theme.sectionTitle

/**
 * A run of jam tracks as a segmented list: suggestions waiting on the room, or the queue itself.
 *
 * Who added each track is shown as their face beside their name, because in a room that is the
 * question people actually have about the queue.
 */
internal fun LazyListScope.jamTrackSection(
    key: String,
    @StringRes title: Int,
    tracks: List<JamTrack>,
    jam: Jam,
    viewModel: JamViewModel,
    supporting: @Composable (JamTrack) -> String = { it.artist },
    trailing: @Composable (JamTrack) -> Unit = {}
) {
    item(key = key + "_header") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.sectionTitle,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = pluralStringResource(R.plurals.jam_song_count, tracks.size, tracks.size),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
    items(count = tracks.size, key = { key + "_" + tracks[it].id }) { index ->
        val track = tracks[index]
        JamTrackRow(
            track = track,
            supporting = supporting(track),
            shape = segmentedShape(index, tracks.size),
            canRemove = jam.isHost || track.addedBy.equals(jam.host, ignoreCase = true),
            onRemove = { viewModel.remove(track.id) },
            trailing = { trailing(track) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (index == 0) 0.dp else SegmentGap)
        )
    }
}

/** What a suggestion still needs, or that you have already said yes to it. */
@Composable
internal fun ProposalAction(track: JamTrack, onApprove: () -> Unit) {
    if (track.approved) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = stringResource(R.string.social_approved),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    } else {
        RowActionButton(stringResource(R.string.common_accept), onApprove)
    }
}

@Composable
private fun JamTrackRow(
    track: JamTrack,
    supporting: String,
    shape: Shape,
    canRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                sizeDp = 52.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(52.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.listTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    val style = MaterialTheme.typography.listSupporting
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = supporting,
                        style = style,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text("·", style = style, color = tint)
                    CuteAvatar(seed = track.addedBy, size = 16.dp, shape = PersonShape)
                    Text(track.addedBy, style = style, color = tint, maxLines = 1)
                }
            }
            trailing()
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
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
