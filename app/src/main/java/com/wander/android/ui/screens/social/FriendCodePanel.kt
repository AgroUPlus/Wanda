package com.wander.android.ui.screens.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.QrCode
import kotlinx.coroutines.delay

/**
 * A code somebody standing next to you can scan to add you.
 *
 * The username search cannot serve this case: one of the two people may not be discoverable, and
 * should not have to become so — permanently, to everyone — in order to be added once by someone
 * who is in the room.
 *
 * Scanned with the phone's own camera rather than one built into Wanda. The QR carries a
 * `wanda://friend/<code>` link, which is the same trick the Agro pairing QR already uses, and it
 * means this feature costs no camera permission for something used a handful of times.
 *
 * The code is re-minted every five minutes while the panel is open and revoked when it closes, so
 * a code photographed off the screen stops working before the person who photographed it gets
 * home. That is the entire security model, and it is why the panel must not be left running in
 * the background — hence the [DisposableEffect].
 */
@Composable
internal fun FriendCodePanel(
    visible: Boolean,
    code: String?,
    onRefresh: () -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        // Keyed on the code itself: each new code starts its own countdown, and re-minting while
        // the panel is open restarts the timer rather than leaving the old one running.
        LaunchedEffect(code) {
            if (code == null) {
                onRefresh()
            } else {
                delay(REFRESH_INTERVAL_MS)
                onRefresh()
            }
        }
        DisposableEffect(Unit) { onDispose(onRevoke) }
    }

    AnimatedVisibility(visible = visible, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                // White, always. A QR code drawn in a surface tint against a surface is a code no
                // camera will find, and the theme has no say in what a scanner can read.
                color = Color.White,
                modifier = Modifier.size(QrSize)
            ) {
                if (code == null) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.padding(QrSize / 3).aspectRatio(1f)
                    )
                } else {
                    QrCode(
                        content = "$FRIEND_LINK_PREFIX$code",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp)
                    )
                }
            }

            Text(
                text = "Have them point their camera at this. It changes every few minutes, and " +
                    "stops working as soon as you close this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            TextButton(onClick = onRefresh, enabled = code != null, shapes = ButtonDefaults.shapes()) {
                Text("New code")
            }
        }
    }
}

/** The deep link the QR carries — see `MainActivity.handleFriendCode`. */
private const val FRIEND_LINK_PREFIX = "wanda://friend/"

/**
 * Slightly under the server's five-minute lifetime, so the code on screen is never the expired
 * one. A code that dies while somebody is lining up their camera is worse than one that changes
 * a few seconds early.
 */
private const val REFRESH_INTERVAL_MS = 4L * 60L * 1000L

private val QrSize = 220.dp
