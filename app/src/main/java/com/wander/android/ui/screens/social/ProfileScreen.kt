package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * One person: who they are, what they are playing, and how much you two overlap.
 *
 * Where a surface is closed the screen says so plainly. An empty chart and a chart someone chose
 * not to share look identical, and only one of them is worth explaining.
 */
@Composable
internal fun ProfileScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 8.dp, top = 4.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            val profile = state.profile
            if (profile == null) {
                if (state.isLoading) {
                    item(key = "loading") { ProfileSkeleton() }
                    return@LazyColumn
                }
                item(key = "missing") {
                Text(
                    text = when {
                        state.error != null -> state.error.orEmpty()
                        // Not found and not visible are the same answer from the server, on
                        // purpose, so this cannot become a way to test whether an account exists.
                        else -> "No account by that name, or they are not visible to you."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp)
                )
            }
            return@LazyColumn
        }

        item(key = "header") { ProfileHero(profile) }

        item(key = "action") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                when (profile.friendState) {
                    FriendState.NONE -> Button(onClick = viewModel::sendRequest, shapes = ButtonDefaults.shapes()) {
                        Text("Add friend")
                    }
                    FriendState.PENDING -> if (profile.outgoing) {
                        OutlinedButton(onClick = viewModel::remove, shapes = ButtonDefaults.shapes()) { Text("Cancel request") }
                    } else {
                        Button(onClick = viewModel::accept, shapes = ButtonDefaults.shapes()) { Text("Accept") }
                        OutlinedButton(onClick = viewModel::remove, shapes = ButtonDefaults.shapes()) { Text("Decline") }
                    }
                    FriendState.ACCEPTED -> OutlinedButton(onClick = viewModel::remove, shapes = ButtonDefaults.shapes()) {
                        Text("Remove friend")
                    }
                }
                TextButton(onClick = viewModel::block, shapes = ButtonDefaults.shapes()) { Text("Block") }
            }
        }

        val now = state.nowPlaying
        item(key = "listening") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(text = "Listening", style = MaterialTheme.typography.titleMedium)
                when {
                    now != null -> {
                        Text(
                            text = now.trackTitle + " · " + now.artistName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.isListeningAlong) {
                            Button(
                                onClick = viewModel::stopListenAlong,
                                modifier = Modifier.padding(top = 8.dp),
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Stop listening along") }
                        } else {
                            Button(
                                onClick = viewModel::startListenAlong,
                                modifier = Modifier.padding(top = 8.dp),
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Listen along") }
                        }
                    }
                    profile.friendState != FriendState.ACCEPTED -> Text(
                        text = "You will see this once you are friends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !profile.showNowPlaying -> Text(
                        text = profile.name + " keeps their listening private.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        text = "Nothing playing right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Their own listening, not just the overlap with yours. The screen showed a taste match
        // and nothing else, so "what does this person actually listen to" — the thing a profile is
        // for — had no answer anywhere in the app.
        val friendStats = state.stats
        if (friendStats != null) {
            item(key = "their-stats") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = profile.name + "'s listening",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            item(key = "their-totals") {
                StatTiles(
                    plays = friendStats.playCount,
                    hours = friendStats.secondsTotal / 3600
                )
            }
            if (friendStats.topArtists.isNotEmpty()) {
                item(key = "their-artists") {
                    StatBars("Top artists", friendStats.topArtists.take(5))
                }
            }
            if (friendStats.topTracks.isNotEmpty()) {
                item(key = "their-tracks") {
                    StatBars("Top tracks", friendStats.topTracks.take(5))
                }
            }
        }

        val match = state.tasteMatch
        item(key = "taste") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(text = "Taste match", style = MaterialTheme.typography.titleMedium)
                when {
                    match != null -> {
                        Text(
                            text = match.score.toString() + "% in common",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        LinearWavyProgressIndicator(
                            progress = { match.score / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    profile.friendState != FriendState.ACCEPTED -> Text(
                        text = "You will see this once you are friends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !profile.showStats -> Text(
                        text = profile.name + " keeps their statistics private.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        text = "Not enough listening between you yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (match != null && match.sharedArtists.isNotEmpty()) {
            item(key = "shared_header") {
                Text(
                    text = "Artists you both play",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )
            }
            items(match.sharedArtists, key = { "shared_" + it.name }) { entry ->
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
        }
    }
}
