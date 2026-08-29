package com.wander.android.core.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Runs [action], asking for local-network access first if this device needs it.
 *
 * Asked here rather than at launch because it is only ever needed for one thing: fetching a track
 * from another device on the same Wi-Fi. Requesting it on first run would put a dialog about local
 * networks in front of someone who may never pair a second device, with nothing on screen to
 * explain it. Tapping "Get" on an offer *is* the explanation.
 *
 * [action] runs either way. A refused permission is not a reason to do nothing: the fetch falls
 * back to the server relay, which needs no local access at all, and it will say so if that fails
 * too. Blocking the tap on a grant the user declined would be worse than the slower route.
 */
@Composable
fun rememberLocalNetworkGate(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(action)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { current() }

    return remember(launcher) {
        {
            val permission = LOCAL_NETWORK_PERMISSION
            if (permission != null && !context.hasLocalNetworkPermission()) {
                launcher.launch(permission)
            } else {
                current()
            }
        }
    }
}
