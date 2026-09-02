package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.permissions.rememberNearbyGate
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * Handing music to the phone next to you, with no network of any kind.
 *
 * The one tier that works in a car, on a plane, at a festival. BLE says who is here — cheaply
 * enough to leave running while the screen is on — and Wi-Fi Direct carries the audio once somebody
 * has been chosen, because BLE at a few hundred kilobits would take a day to move an album.
 *
 * Everything stops when this screen goes away. Advertising is a broadcast to a room and a link is a
 * radio held open; neither is something to leave running behind a screen nobody is looking at.
 */
@Composable
internal fun OffGridScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: OffGridViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val withNearby = rememberNearbyGate()

    // Not `stop()` on every recomposition — only when the screen is actually leaving.
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Off-grid",
                style = MaterialTheme.typography.displaySmall,
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
                        ) { Text("Stop sharing") }
                    } else {
                        Button(
                            // The permissions are asked at this tap, where the screen above says
                            // what they are for. Denied, the action still runs and reports honestly.
                            onClick = { withNearby { viewModel.startSharing() } },
                            shapes = ButtonDefaults.shapes()
                        ) { Text("Be findable") }
                    }
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
                        isLinked = state.linkedTo == peer.beacon.deviceId,
                        isBusy = state.isConnecting,
                        onClick = { viewModel.connect(peer) }
                    )
                }
            }
        }
    }
}

/** Why this screen exists, in the two sentences that decide whether anyone turns it on. */
@Composable
private fun OffGridExplainer() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Encrypted, and nothing leaves the two phones",
                style = MaterialTheme.typography.titleSmall
            )
        }
        Text(
            text = "Share music with someone beside you, over Bluetooth and Wi-Fi Direct. " +
                "No router, no mobile data, no server. You are only findable while this " +
                "screen is open.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        LoadingIndicator(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Looking for phones nearby…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One device in the room.
 *
 * Named by its identity fingerprint rather than by a device name: the beacon deliberately carries
 * no name, because a Bluetooth name is usually its owner's and it would be broadcast to everybody
 * — see `OffGridBeacon`. The fingerprint is also what the pairing check verifies, so showing it is
 * showing the thing that was actually confirmed.
 */
@Composable
private fun PeerRow(
    peer: NearbyPeers.Peer,
    isLinked: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text("Device " + peer.beacon.shortFingerprint()) },
        supportingContent = {
            Text(if (isLinked) "Connected, encrypted" else signalWord(peer.rssi))
        },
        leadingContent = {
            Icon(Icons.Rounded.BluetoothSearching, contentDescription = null)
        },
        trailingContent = {
            when {
                isLinked -> Icon(Icons.Rounded.Lock, contentDescription = "Linked")
                isBusy -> LoadingIndicator(modifier = Modifier.size(20.dp))
                else -> null
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (!isLinked && !isBusy) {
        Row(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp)) {
            FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
                Text("Connect")
            }
        }
    }
}

/**
 * Signal as a word, not a number.
 *
 * dBm means nothing to anybody, and the only decision it informs is "is that the phone in my hand
 * or one two rooms away".
 */
private fun signalWord(rssi: Int): String = when {
    rssi > -55 -> "Right here"
    rssi > -70 -> "Nearby"
    else -> "Further away"
}

@Composable
private fun UnsupportedNotice() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(20.dp)
    ) {
        Text(
            text = "This phone cannot be found off-grid.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Being findable needs Bluetooth peripheral mode, and some phones simply do not " +
                "have it. You can still listen along over Wi-Fi or through your server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
