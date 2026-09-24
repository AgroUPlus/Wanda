package com.wander.android.ui.screens.social

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.permissions.rememberNearbyGate
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.components.rememberHaptics

/**
 * Handing music to the phone next to you, with no network of any kind.
 *
 * BLE says who is here and Wi-Fi Direct carries the audio once somebody has been chosen.
 */
@Composable
internal fun OffGridScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: OffGridViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val withNearby = rememberNearbyGate()
    val haptics = rememberHaptics()

    DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(R.string.common_off_grid),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (!state.isSupported) {
            UnsupportedNotice()
            return
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "explainer") { OffGridExplainer() }

            state.error?.let { message ->
                item(key = "error") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item(key = "toggle") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    if (state.isAdvertising) {
                        FilledTonalButton(
                            onClick = viewModel::stop,
                            shapes = ButtonDefaults.shapes()
                        ) { Text(stringResource(R.string.social_stop_sharing)) }
                    } else {
                        Button(
                            onClick = {
                                haptics.confirmed()
                                withNearby { viewModel.startSharing() }
                            },
                            shapes = ButtonDefaults.shapes()
                        ) { Text(stringResource(R.string.social_findable)) }
                    }
                }
            }

            if (state.links.isNotEmpty()) {
                item(key = "links_header") { SectionHeader("Connected") }
                items(state.links, key = { "link-${it.deviceId}" }) { link ->
                    ConnectedRow(link = link, onDisconnect = viewModel::disconnect)
                }
                item(key = "listen_along") {
                    ListenAlongRow(
                        isFollowing = state.isFollowing,
                        nowPlaying = state.followingNowPlaying,
                        unresolvable = state.followingUnresolvable,
                        onStart = viewModel::listenAlongOffGrid,
                        onStop = viewModel::stopListenAlong
                    )
                }
            }

            if (state.isAdvertising) {
                item(key = "peers_header") { SectionHeader("In the room") }
                if (state.isSearching) {
                    item(key = "searching") { SearchingRow() }
                }
                items(state.peers, key = { it.beacon.deviceId }) { peer ->
                    PeerRow(
                        peer = peer,
                        isLinked = state.isLinkedTo(peer.beacon.deviceId),
                        isBusy = state.isConnecting,
                        onClick = { viewModel.connect(peer) }
                    )
                }
            }
        }
    }
}
