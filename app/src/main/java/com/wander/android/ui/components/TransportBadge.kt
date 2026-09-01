package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.ResolvedFrom

/**
 * Where the audio is coming from, at a glance.
 *
 * The six tiers differ in ways a listener has a real stake in — whether it costs data, whether it
 * works with the router unplugged, and whether anyone in between can hear it — and until now the
 * only trace of which one was in use was a clause at the end of a sentence. A badge is legible
 * without reading.
 *
 * Colour carries no meaning on its own: every badge has an icon and a word, because a colour-coded
 * status is unreadable to a colour-blind user and invisible in a screenshot. The lock is drawn only
 * where the transport is genuinely encrypted end to end, and that is the one thing here that must
 * never be decorative — a padlock on a stream anyone on the Wi-Fi can hear is a lie, and a worse
 * one than saying nothing.
 */
@Composable
internal fun TransportBadge(from: ResolvedFrom?, modifier: Modifier = Modifier) {
    val style = from.style()
    Row(
        modifier = modifier
            .background(
                color = style.container(),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            // One description for the whole badge: a screen reader announcing an icon, a padlock
            // and a word separately turns a glance into three.
            .clearAndSetSemantics { contentDescription = style.spoken },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.onContainer(),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall,
            color = style.onContainer()
        )
        if (style.encrypted) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = style.onContainer(),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

/** The label, the icon, and whether the padlock is honest for this tier. */
internal data class TransportStyle(
    val label: String,
    val spoken: String,
    val icon: ImageVector,
    val encrypted: Boolean,
    val container: @Composable () -> Color,
    val onContainer: @Composable () -> Color
)

internal fun ResolvedFrom?.style(): TransportStyle = when (this) {
    ResolvedFrom.LOCAL_STORAGE -> TransportStyle(
        label = "On device",
        spoken = "Playing from this device. Nothing leaves the phone.",
        icon = Icons.Filled.Storage,
        // Not marked encrypted, because there is no transport to encrypt. A padlock here would
        // imply a protection was applied rather than not being needed.
        encrypted = false,
        container = { MaterialTheme.colorScheme.surfaceVariant },
        onContainer = { MaterialTheme.colorScheme.onSurfaceVariant }
    )
    ResolvedFrom.NAVIDROME -> TransportStyle(
        label = "Navidrome",
        spoken = "Streamed from your own Navidrome server.",
        icon = Icons.Filled.Router,
        // HTTPS if the user configured it, plain HTTP if not, and this cannot tell which. Claiming
        // encryption on a self-hosted address that might be `http://` would be a guess.
        encrypted = false,
        container = { MaterialTheme.colorScheme.secondaryContainer },
        onContainer = { MaterialTheme.colorScheme.onSecondaryContainer }
    )
    ResolvedFrom.YOUTUBE_MUSIC -> TransportStyle(
        label = "Stream",
        spoken = "Matched and streamed from YouTube Music. Uses data.",
        icon = Icons.Filled.Cloud,
        encrypted = false,
        container = { MaterialTheme.colorScheme.tertiaryContainer },
        onContainer = { MaterialTheme.colorScheme.onTertiaryContainer }
    )
    ResolvedFrom.P2P_DIRECT -> TransportStyle(
        label = "LAN",
        spoken = "Streamed directly from the other device over your local network, encrypted.",
        icon = Icons.Filled.Wifi,
        encrypted = true,
        container = { MaterialTheme.colorScheme.primaryContainer },
        onContainer = { MaterialTheme.colorScheme.onPrimaryContainer }
    )
    ResolvedFrom.P2P_OFFGRID -> TransportStyle(
        label = "Off-grid",
        spoken = "Streamed phone to phone over a direct radio link, with no network involved, encrypted.",
        icon = Icons.Filled.Bluetooth,
        encrypted = true,
        container = { MaterialTheme.colorScheme.primaryContainer },
        onContainer = { MaterialTheme.colorScheme.onPrimaryContainer }
    )
    ResolvedFrom.AGRO_RELAY -> TransportStyle(
        label = "Relay",
        spoken = "Streamed through your Agro server, encrypted end to end. The server cannot hear it.",
        icon = Icons.Filled.Cloud,
        encrypted = true,
        container = { MaterialTheme.colorScheme.tertiaryContainer },
        onContainer = { MaterialTheme.colorScheme.onTertiaryContainer }
    )
    null -> TransportStyle(
        label = "Finding…",
        spoken = "Looking for a way to play this.",
        icon = Icons.Filled.PhoneAndroid,
        encrypted = false,
        container = { MaterialTheme.colorScheme.surfaceVariant },
        onContainer = { MaterialTheme.colorScheme.onSurfaceVariant }
    )
}
