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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.permissions.rememberLocalNetworkGate
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * A jam: one queue several people build, and one track the whole room is on.
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
    val withLocalNetwork = rememberLocalNetworkGate()

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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(R.string.action_jam),
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
                onCreate = { mode -> withLocalNetwork { viewModel.create(mode) } },
                onJoin = { code -> withLocalNetwork { viewModel.join(code) } },
                friendJams = state.friendJams,
                onJoinFriendJam = { id -> withLocalNetwork { viewModel.joinFriendJam(id) } },
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
                JamNowPlayingCard(jam, state.unresolvable, state.outOfSync, viewModel)
            }
            item(key = "room") {
                JamRoomCard(jam, state.isRadioEnabled, viewModel)
            }

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
                item(key = "proposals-header") { JamSectionLabel("Waiting on the room") }
                items(jam.proposals, key = { it.id }) { track ->
                    ProposalRow(track, jam, viewModel)
                }
            }

            item(key = "queue-header") { JamSectionLabel("Up next") }
            if (jam.queue.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        text = stringResource(R.string.social_nothing_queued_play_anything_goes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
            items(jam.queue, key = { it.id }) { track ->
                JamQueueRow(track, jam, viewModel)
            }
        }
    }
}
