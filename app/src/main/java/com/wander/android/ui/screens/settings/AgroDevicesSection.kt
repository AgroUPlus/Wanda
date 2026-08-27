package com.wander.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode
import com.wander.android.ui.components.ListeningGreen

/**
 * The other devices registered with Agro.
 *
 * A live device gets the same green dot the Home session row uses — the state worth spotting at a
 * glance is "something is playing over there", and a plain text row buried among settings rows was
 * easy to miss entirely.
 *
 * Agro keeps **one** session per user rather than one per device, so Resume always picks up the
 * most recent one, whichever device produced it. The copy says so instead of implying you can pull
 * a specific device's stream.
 *
 * These are **your own** account's devices, and this one is never among them — it is the device
 * you are holding. So an empty list is the ordinary state for anyone signed in on one phone, and
 * the section says that rather than vanishing: a heading that is simply absent looks like a
 * feature that failed to load, which is exactly how it was read.
 */
internal fun LazyListScope.agroDevicesSection(
    devices: List<AgroNode>,
    handoff: AgroHandoffState?,
    isResuming: Boolean,
    onResume: (AgroHandoffState) -> Unit
) {
    item(key = "agro_devices_header") { SettingsSection("Devices") }

    if (devices.isEmpty()) {
        item(key = "agro_devices_empty") {
            Text(
                text = "Nothing else is signed in. Other devices using this Agro account " +
                    "show up here — this one never does.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
        return
    }

    items(items = devices, key = { "agro_device_${it.deviceId}" }) { node ->
        val resumable = handoff?.takeIf { it.deviceId == node.deviceId }
        DeviceRow(
            node = node,
            isResuming = isResuming,
            onResume = resumable?.let { { onResume(it) } }
        )
    }
}

@Composable
private fun DeviceRow(
    node: AgroNode,
    isResuming: Boolean,
    onResume: (() -> Unit)?
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (node.isOnline) ListeningGreen
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(text = node.petname, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(if (node.isOnline) "Listening now" else "Away")
                        append(" · ")
                        append(node.clientType.replaceFirstChar(Char::uppercase))
                        node.currentTrack?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (node.isOnline) ListeningGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onResume != null) {
                Text(
                    text = if (isResuming) "Resuming…" else "Continue here",
                    style = MaterialTheme.typography.labelLarge,
                    color = ListeningGreen
                )
            }
        }
    }

    if (onResume == null) content() else Surface(onClick = onResume, content = content)
}
