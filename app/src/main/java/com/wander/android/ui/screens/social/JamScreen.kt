package com.wander.android.ui.screens.social

import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.sources.agro.FriendJam
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.data.sources.agro.JamTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * A jam: one queue several people build, and one track the whole room is on.
 *
 * The screen is arranged around that second fact, because it is the thing every earlier version
 * left implicit. What the room is hearing leads; what it will hear comes next; what has been
 * suggested but not agreed sits apart, because it is not in the queue yet and showing it as though
 * it were is what made voting feel like it did nothing.
 *
 * Nothing here starts or advances playback. The server decides both.
 */
@Composable
internal fun JamScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit = {},
    initialCode: String? = null,
    viewModel: JamViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialCode) {
        val clean = initialCode?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
        if (!clean.isNullOrEmpty() && clean != "CODE" && state.jam == null) {
            viewModel.join(clean)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Jam",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (!state.isPaired) {
            NotPairedNotice(onOpenSettings = onOpenSettings)
            return
        }

        val jam = state.jam
        if (jam == null) {
            StartOrJoin(
                onCreate = viewModel::create,
                onJoin = viewModel::join,
                friendJams = state.friendJams,
                onJoinFriendJam = viewModel::joinFriendJam,
                error = state.error,
                initialCode = initialCode,
                modifier = Modifier.padding(24.dp)
            )
            return
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "now") {
                NowPlayingCard(jam, state.unresolvable, state.outOfSync, viewModel)
            }
            item(key = "room") { RoomCard(jam, state.isRadioEnabled, viewModel) }

            state.error?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            if (jam.proposals.isNotEmpty()) {
                item(key = "proposals-header") { SectionLabel("Waiting on the room") }
                items(jam.proposals, key = { it.id }) { track ->
                    ProposalRow(track, jam, viewModel)
                }
            }

            item(key = "queue-header") { SectionLabel("Up next") }
            if (jam.queue.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        text = "Nothing queued. Play anything and it goes to the room.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
            items(jam.queue, key = { it.id }) { track ->
                QueueRow(track, jam, viewModel)
            }
        }
    }
}

/** What the room is hearing, with a bar that moves because the server said where it is. */
@Composable
private fun NowPlayingCard(
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
                    text = "Nothing playing yet",
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
            // Ticked locally from the last position the server sent, rather than animated toward
            // the end of the track.
            //
            // The previous version handed `animateFloatAsState` a target of 1f, and an animation
            // starts at the first target it is given — so it was *already* at 1f on the first
            // frame and had nothing to animate. `maxOf(progress, animated)` then pinned the bar
            // full for the whole song.
            //
            // Elapsed wall-clock since the frame arrived is used rather than the server's
            // `startedAt`, because that needs the phone's clock to agree with the server's and a
            // device with a skewed clock would draw a position the room is nowhere near. Time
            // *since we heard* is the same on any clock.
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
                    // Twice a second: smooth enough to read as moving, cheap enough that it is not
                    // a reason to keep the screen awake.
                    delay(500)
                }
            }
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            // Voting to skip, with the room's tally. Everybody counts here, including whoever
            // suggested it: wanting a track gone is not the same act as having wanted it in.
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
                        text = "You voted to skip · ${now.skipVotes}/${now.skipsNeeded}",
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
                        Text("Vote to skip")
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

            // Offered rather than done silently: the device was paused or seeked by its owner, and
            // yanking it back without asking would be the follower fighting the person holding it.
            AnimatedVisibility(visible = outOfSync && unresolvable == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = "You've drifted from the room.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = viewModel::resync, shapes = ButtonDefaults.shapes()) { Text("Rejoin") }
                }
            }

            AnimatedVisibility(visible = unresolvable != null) {
                Text(
                    text = "You don't have “${unresolvable.orEmpty()}” — " +
                        "the room is still playing it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** The code, who is here, and the one rule the creator can change. */
@Composable
private fun RoomCard(jam: Jam, isRadioEnabled: Boolean, viewModel: JamViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Join code", style = MaterialTheme.typography.labelMedium)
                    Text(jam.code, style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = {
                    val shareUrl = viewModel.shareUrl(jam.code)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, "Join my music Jam on Wanda! $shareUrl")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Jam Link"))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share jam link")
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(jam.code)) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy the join code")
                }
            }

            // Member Avatars & List
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                com.wander.android.ui.components.AvatarGroup(
                    usernames = jam.members,
                    size = 34.dp,
                    overlap = 10.dp,
                    maxDisplay = 6
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${jam.members.size} ${if (jam.members.size == 1) "member" else "members"} in room",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = jam.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (jam.isHost) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        checked = jam.mode == JamMode.DEMOCRACY,
                        onCheckedChange = { viewModel.setMode(JamMode.DEMOCRACY) }
                    ) { Text("Vote to add") }
                    ToggleButton(
                        checked = jam.mode == JamMode.OPEN,
                        onCheckedChange = { viewModel.setMode(JamMode.OPEN) }
                    ) { Text("Anyone adds") }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = if (jam.mode == JamMode.DEMOCRACY) {
                    "Suggestions need ${jam.approvalsNeeded} other " +
                        (if (jam.approvalsNeeded == 1L) "person" else "people") + " to agree."
                } else {
                    "Anyone can add straight to the queue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (jam.isHost) {
                // Jam Radio setting - Host only
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jam Radio", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Auto-blends the room's music tastes when the queue runs out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRadioEnabled,
                        onCheckedChange = viewModel::setJamRadioEnabled
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open to friends", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (jam.openToFriends) {
                                "Your friends can see this jam and join without the code."
                            } else {
                                "Only people you give the code to can join."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = jam.openToFriends,
                        onCheckedChange = viewModel::setOpenToFriends
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::leave,
                modifier = Modifier.padding(top = 16.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(if (jam.isHost) "End jam" else "Leave")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** A suggestion, with how far off the room is from accepting it. */
@Composable
private fun ProposalRow(track: JamTrack, jam: Jam, viewModel: JamViewModel) {
    TrackRow(
        track = track,
        subtitle = "${track.artist} · ${track.stillNeeded} more to go",
        jam = jam,
        viewModel = viewModel
    ) {
        // Approving is one-way, so once yours is in there is nothing left to press.
        if (track.approved) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "You approved this",
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            FilledTonalButton(onClick = { viewModel.approve(track.id) }, shapes = ButtonDefaults.shapes()) { Text("Accept") }
        }
    }
}

@Composable
private fun QueueRow(track: JamTrack, jam: Jam, viewModel: JamViewModel) {
    TrackRow(
        track = track,
        subtitle = "${track.artist} · added by ${track.addedBy}",
        jam = jam,
        viewModel = viewModel
    ) {}
}

@Composable
private fun TrackRow(
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
        // Yours to withdraw, or the creator's to drop — the same rule the server keeps.
        if (jam.isHost || track.addedBy.equals(jam.host, ignoreCase = true)) {
            IconButton(onClick = { viewModel.remove(track.id) }) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun StartOrJoin(
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
            text = "One queue, everyone in it. The room plays the same track at the same time, " +
                "and while you are in a jam anything you play goes to the room instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // A friend's open jam is one tap, and no code to be dictated. Only jams whose creator has
        // opened them up appear here — being someone's friend is not consent to be pulled in.
        if (friendJams.isNotEmpty()) {
            Text("Your friends are jamming", style = MaterialTheme.typography.titleSmall)
            friendJams.forEach { open ->
                Card(
                    onClick = { onJoinFriendJam(open.id) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${open.host}'s jam", style = MaterialTheme.typography.titleMedium)
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
            Text("Start a jam")
        }

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Join code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { onJoin(code) },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes()
        ) {
            Text("Join")
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
