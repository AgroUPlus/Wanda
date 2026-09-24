package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridLink

/** Why this screen exists, in the two sentences that decide whether anyone turns it on. */
@Composable
internal fun OffGridExplainer() {
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
                text = stringResource(R.string.social_encrypted_nothing_leaves_two_phones),
                style = MaterialTheme.typography.titleSmall
            )
        }
        Text(
            text = stringResource(R.string.social_share_music_someone_beside_over),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SearchingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        LoadingIndicator(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.social_looking_phones_nearby),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One device in the room.
 */
@Composable
internal fun PeerRow(
    peer: NearbyPeers.Peer,
    isLinked: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.common_device, peer.beacon.shortFingerprint())) },
        supportingContent = {
            Text(if (isLinked) "Connected, encrypted" else signalWord(peer.rssi))
        },
        leadingContent = {
            Icon(Icons.Rounded.BluetoothSearching, contentDescription = null)
        },
        trailingContent = {
            when {
                isLinked -> Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.social_linked))
                isBusy -> LoadingIndicator(modifier = Modifier.size(20.dp))
                else -> null
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (!isLinked && !isBusy) {
        Row(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp)) {
            FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.social_connect))
            }
        }
    }
}

/**
 * Following what the linked peer plays, with no server in between.
 */
@Composable
internal fun ListenAlongRow(
    isFollowing: Boolean,
    nowPlaying: String?,
    unresolvable: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    ListItem(
        headlineContent = { Text(if (isFollowing) "Listening along" else "Listen along") },
        supportingContent = {
            Text(
                when {
                    unresolvable != null -> "Can't find \"$unresolvable\" on this phone"
                    nowPlaying != null -> nowPlaying
                    isFollowing -> "Waiting for them to play something"
                    else -> "Play whatever the linked device plays"
                }
            )
        },
        leadingContent = { Icon(Icons.Rounded.BluetoothSearching, contentDescription = null) },
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp)) {
        FilledTonalButton(
            onClick = if (isFollowing) onStop else onStart,
            shapes = ButtonDefaults.shapes()
        ) { Text(if (isFollowing) "Stop following" else "Listen along") }
    }
}

/**
 * A live link, on whichever phone is reading it.
 */
@Composable
internal fun ConnectedRow(
    link: OffGridLink,
    onDisconnect: (OffGridLink) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.common_device, shortId(link.deviceId))) },
        supportingContent = {
            Text(
                when (link.role) {
                    OffGridLink.Role.INITIATED -> "Connected, encrypted"
                    OffGridLink.Role.ACCEPTED -> "Connected to you, encrypted"
                }
            )
        },
        leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.padding(start = 56.dp, end = 20.dp, bottom = 8.dp)) {
        FilledTonalButton(
            onClick = { onDisconnect(link) },
            shapes = ButtonDefaults.shapes()
        ) { Text(stringResource(R.string.social_disconnect)) }
    }
}

private fun shortId(deviceId: Int): String = "%08X".format(deviceId).chunked(4).joinToString(" ")

private fun signalWord(rssi: Int): String = when {
    rssi > -55 -> "Right here"
    rssi > -70 -> "Nearby"
    else -> "Further away"
}

@Composable
internal fun UnsupportedNotice() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.social_phone_cannot_found_off_grid),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.social_being_findable_needs_bluetooth_peripheral),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
