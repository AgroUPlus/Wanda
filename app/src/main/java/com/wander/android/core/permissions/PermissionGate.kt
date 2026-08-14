package com.wander.android.core.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Audio access plus the ability to ask for it, for callers that prompt on a button instead. */
@Stable
class PermissionGate internal constructor(
    private val hasAudioState: State<Boolean>,
    private val onRequest: () -> Unit
) {
    val hasAudio: Boolean get() = hasAudioState.value

    fun request() = onRequest()
}

/**
 * Tracks the audio and notification permissions, re-checking on resume so a grant made in system
 * settings is picked up without a restart.
 *
 * The manifest declared both permissions but nothing ever asked for them — local music came back
 * empty and the playback notification never appeared.
 *
 * @param onAudioGranted invoked when audio access is available; used to kick off the library scan.
 * @param requestOnStart ask immediately. False for the welcome flow, which prompts on a button so
 *   the user has been told what the permission is for before the system dialog appears.
 */
@Composable
fun rememberPermissionGate(
    onAudioGranted: suspend () -> Unit,
    requestOnStart: Boolean = true
): PermissionGate {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasAudioState = remember { mutableStateOf(context.hasAudioPermission()) }
    var hasAudio by hasAudioState

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasAudio = context.hasAudioPermission() }

    val request: () -> Unit = {
        val missing = buildList {
            if (!context.hasAudioPermission()) add(AUDIO_PERMISSION)
            NOTIFICATION_PERMISSION?.takeIf { !context.hasPermission(it) }?.let(::add)
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    LaunchedEffect(requestOnStart) {
        if (requestOnStart) request()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasAudio = context.hasAudioPermission()
        }
    }

    LaunchedEffect(hasAudio) {
        if (hasAudio) onAudioGranted()
    }

    return remember(hasAudioState) { PermissionGate(hasAudioState, request) }
}
