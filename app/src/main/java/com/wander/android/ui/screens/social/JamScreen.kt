package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.permissions.rememberLocalNetworkGate
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.rememberConfirmState
import com.wander.android.ui.theme.CoverTintedTheme
import com.wander.android.ui.theme.buttonSmall
import com.wander.android.ui.theme.rememberCoverSeedColor
import com.wander.android.ui.theme.screenTitle

/**
 * A jam: one queue several people build, and one track the whole room is on.
 *
 * The whole screen takes its colour from the cover the room is hearing, so the jam looks like the
 * song rather than like the rest of the app — the same scoped tint the player uses.
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
    val withLocalNetwork = rememberLocalNetworkGate() // NOSONAR: used inside the onCreate/onJoin lambdas below

    LaunchedEffect(initialCode) {
        val clean = initialCode?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
        if (!clean.isNullOrEmpty() && clean != "CODE" && state.jam == null) {
            viewModel.join(clean)
        }
    }

    val base = MaterialTheme.colorScheme
    CoverTintedTheme(
        seedColor = rememberCoverSeedColor(state.jam?.nowPlaying?.artworkUrl),
        base = base,
        dark = base.surface.luminance() < 0.5f,
        amoled = false
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                JamTopBar(contentPadding, state.jam, onCollapse = onBack, onExit = viewModel::leave)

                if (!state.isPaired) {
                    NotPairedNotice(onOpenSettings = onOpenSettings)
                    return@Column
                }

                val jam = state.jam
                if (jam == null) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "start") {
                            StartOrJoin(
                                onCreate = { mode -> withLocalNetwork { viewModel.create(mode) } },
                                onJoin = { code -> withLocalNetwork { viewModel.join(code) } },
                                friendJams = state.friendJams,
                                onJoinFriendJam = { id -> withLocalNetwork { viewModel.joinFriendJam(id) } },
                                error = state.error,
                                initialCode = initialCode,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                        // Under the way in rather than above it: someone opening this screen is
                        // most often here to start a jam, and last night's can wait a scroll.
                        items(state.recaps, key = { "recap-${it.id}" }) { recap ->
                            JamRecapCard(
                                stored = recap,
                                saved = recap.id in state.savedRecaps,
                                onSave = { title -> viewModel.saveRecap(recap, title) },
                                onDismiss = { viewModel.dismissRecap(recap.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateItem()
                            )
                        }
                    }
                    return@Column
                }

                JamRoom(jam, state, contentPadding, viewModel)
            }
        }
    }
}

@Composable
private fun JamRoom(jam: Jam, state: JamUiState, contentPadding: PaddingValues, viewModel: JamViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "now") { JamNowPlayingCard(jam, state.unresolvable, state.outOfSync, viewModel) }
        item(key = "room") { JamRoomCard(jam, state.isRadioEnabled, viewModel) }

        state.error?.let { message ->
            item(key = "error") {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
                )
            }
        }

        if (jam.proposals.isNotEmpty()) {
            jamTrackSection(
                key = "proposals",
                title = R.string.jam_waiting_on_room,
                tracks = jam.proposals,
                jam = jam,
                viewModel = viewModel,
                supporting = { stringResource(R.string.social_more_go, it.artist, it.stillNeeded) }
            ) { track ->
                ProposalAction(track, onApprove = { viewModel.approve(track.id) })
            }
        }

        jamTrackSection("queue", R.string.jam_up_next, jam.queue, jam, viewModel)
        if (jam.queue.isEmpty()) {
            item(key = "queue-empty") {
                Text(
                    text = stringResource(R.string.social_nothing_queued_play_anything_goes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

/**
 * Collapse, the screen's name, and the way out. The host ends the room for everyone, so that asks
 * first; anyone else only leaves it, which needs no confirmation.
 */
@Composable
private fun JamTopBar(contentPadding: PaddingValues, jam: Jam?, onCollapse: () -> Unit, onExit: () -> Unit) {
    val confirm = rememberConfirmState()
    val endJam = ConfirmRequest(
        title = stringResource(R.string.jam_end_confirm_title),
        message = stringResource(R.string.jam_end_confirm_message),
        confirmLabel = stringResource(R.string.jam_end),
        onConfirm = onExit
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding.headerInset())
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.jam_collapse),
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = stringResource(R.string.action_jam),
            style = MaterialTheme.typography.screenTitle,
            modifier = Modifier.weight(1f)
        )
        if (jam != null) {
            FilledTonalButton(
                onClick = { if (jam.isHost) confirm.ask(endJam) else onExit() },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = stringResource(if (jam.isHost) R.string.jam_end else R.string.jam_quit),
                    style = MaterialTheme.typography.buttonSmall
                )
            }
        }
    }
}
