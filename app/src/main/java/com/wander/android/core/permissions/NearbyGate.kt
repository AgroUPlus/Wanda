package com.wander.android.core.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The radios the off-grid tier needs, asked for together.
 *
 * Advertising, scanning and connecting are three separate grants, and Wi-Fi Direct needs a fourth.
 * They are asked as one batch because they are one capability from the user's point of view: there
 * is nothing useful to do having granted scanning but not advertising.
 *
 * `NEARBY_WIFI_DEVICES` is the one that matters most and is easiest to miss. Its absence does not
 * raise an error — the framework simply never finds a peer, which reads exactly like a feature that
 * does not work. That is the shape of the bug that cost this project a whole debugging session on
 * `ACCESS_LOCAL_NETWORK`: declared in the manifest, never requested, silently refused.
 */
private val NEARBY_PERMISSIONS: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Not a fallback but the only thing that works here, and its absence is the same silent
        // nothing described above. The `BLUETOOTH_*` grants are API 31 and later; on 26..30 a BLE
        // scan is gated on location instead, and the legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` pair the
        // manifest declares for those releases is install-time and needs no asking. Without this
        // branch the list was empty below 31 and the gate asked for nothing at all.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Wi-Fi Direct's own gate on 31..32, where `NEARBY_WIFI_DEVICES` does not yet exist:
        // `discoverPeers` and `requestPeers` return an empty peer list without it.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/**
 * Runs an action, asking for the nearby-device permissions first if any are missing.
 *
 * The wrapper form — `withNearby { viewModel.startAdvertising() }` — because a screen has several
 * actions behind the same grant and their arguments are not known until the tap. It follows
 * [rememberLocalNetworkGate] exactly, including the part that matters most:
 *
 * **The action runs either way.** A refused grant is not a reason to do nothing. Advertising will
 * fail and say so, and the listen-along it was for still works over the relay — blocking the tap on
 * a permission the user declined would remove a working path to punish them for a choice.
 */
@Composable
fun rememberNearbyGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Cleared before running, so a refusal cannot leave an action armed for the next answer.
        val action = pending.value
        pending.value = null
        action?.invoke()
    }

    return remember(launcher) {
        { action ->
            val missing = NEARBY_PERMISSIONS.filterNot { context.hasPermission(it) }
            if (missing.isEmpty()) {
                action()
            } else {
                pending.value = action
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}
