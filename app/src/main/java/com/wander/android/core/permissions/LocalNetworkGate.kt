package com.wander.android.core.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * The same gate for a screen with several actions behind it, each carrying its own argument.
 *
 * Returns a wrapper: `withLocalNetwork { viewModel.join(code) }`. One launcher for the screen, and
 * the action that asked for it is held until the answer comes back — which is why this cannot just
 * be [rememberLocalNetworkGate] called once per button, as each call would need its own launcher
 * registered at composition time and the arguments are not known until the tap.
 */
@Composable
fun rememberLocalNetworkGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Run either way, and clear first so a refused grant cannot leave an action armed for the
        // next unrelated answer. See the note above: the relay covers a refusal.
        val action = pending.value
        pending.value = null
        action?.invoke()
    }

    return remember(launcher) {
        { action ->
            val permission = LOCAL_NETWORK_PERMISSION
            if (permission != null && !context.hasLocalNetworkPermission()) {
                pending.value = action
                launcher.launch(permission)
            } else {
                action()
            }
        }
    }
}
