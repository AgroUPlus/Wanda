package com.wander.android.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.wander.android.R
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
    // clearAndSetSemantics takes a plain lambda, so the string is read before it.
    val spoken = stringResource(style.spoken)
    Row(
        modifier = modifier
            .background(
                color = style.container(),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            // One description for the whole badge: a screen reader announcing an icon, a padlock
            // and a word separately turns a glance into three.
            .clearAndSetSemantics { contentDescription = spoken },
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
            text = stringResource(style.label),
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

/**
 * The label, the icon, and whether the padlock is honest for this tier.
 *
 * The two strings are resource ids rather than text: [style] is a plain function, so it has no
 * composition to resolve them in. They are read where the badge is drawn.
 */
internal data class TransportStyle(
    @StringRes val label: Int,
    @StringRes val spoken: Int,
    val icon: ImageVector,
    val encrypted: Boolean,
    val container: @Composable () -> Color,
    val onContainer: @Composable () -> Color
)

internal fun ResolvedFrom?.style(): TransportStyle = when (this) {
    ResolvedFrom.LOCAL_STORAGE -> TransportStyle(
        label = R.string.common_device_2,
        spoken = R.string.transport_spoken_device,
        icon = Icons.Filled.Storage,
        // Not marked encrypted, because there is no transport to encrypt. A padlock here would
        // imply a protection was applied rather than not being needed.
        encrypted = false,
        container = { MaterialTheme.colorScheme.surfaceVariant },
        onContainer = { MaterialTheme.colorScheme.onSurfaceVariant }
    )
    ResolvedFrom.NAVIDROME -> TransportStyle(
        label = R.string.common_navidrome,
        spoken = R.string.transport_spoken_navidrome,
        icon = Icons.Filled.Router,
        // HTTPS if the user configured it, plain HTTP if not, and this cannot tell which. Claiming
        // encryption on a self-hosted address that might be `http://` would be a guess.
        encrypted = false,
        container = { MaterialTheme.colorScheme.secondaryContainer },
        onContainer = { MaterialTheme.colorScheme.onSecondaryContainer }
    )
    ResolvedFrom.YOUTUBE_MUSIC -> TransportStyle(
        label = R.string.common_stream,
        spoken = R.string.transport_spoken_stream,
        icon = Icons.Filled.Cloud,
        encrypted = false,
        container = { MaterialTheme.colorScheme.tertiaryContainer },
        onContainer = { MaterialTheme.colorScheme.onTertiaryContainer }
    )
    ResolvedFrom.P2P_DIRECT -> TransportStyle(
        label = R.string.common_lan,
        spoken = R.string.transport_spoken_lan,
        icon = Icons.Filled.Wifi,
        encrypted = true,
        container = { MaterialTheme.colorScheme.primaryContainer },
        onContainer = { MaterialTheme.colorScheme.onPrimaryContainer }
    )
    ResolvedFrom.P2P_OFFGRID -> TransportStyle(
        label = R.string.common_off_grid,
        spoken = R.string.transport_spoken_offgrid,
        icon = Icons.Filled.Bluetooth,
        encrypted = true,
        container = { MaterialTheme.colorScheme.primaryContainer },
        onContainer = { MaterialTheme.colorScheme.onPrimaryContainer }
    )
    ResolvedFrom.AGRO_RELAY -> TransportStyle(
        label = R.string.common_relay,
        spoken = R.string.transport_spoken_relay,
        icon = Icons.Filled.Cloud,
        encrypted = true,
        container = { MaterialTheme.colorScheme.tertiaryContainer },
        onContainer = { MaterialTheme.colorScheme.onTertiaryContainer }
    )
    null -> TransportStyle(
        label = R.string.common_finding,
        spoken = R.string.transport_spoken_finding,
        icon = Icons.Filled.PhoneAndroid,
        encrypted = false,
        container = { MaterialTheme.colorScheme.surfaceVariant },
        onContainer = { MaterialTheme.colorScheme.onSurfaceVariant }
    )
}
