package com.wander.android.core.permissions

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Audio access plus the ability to ask for it, for callers that prompt on a button instead. */
@Stable
class PermissionGate internal constructor(
    private val hasAudioState: State<Boolean>,
    private val audioBlockedState: State<Boolean>,
    private val onRequest: () -> Unit
) {
    val hasAudio: Boolean get() = hasAudioState.value

    /**
     * The system has stopped showing the audio dialog — denied with "don't ask again", or twice —
     * so [request] opens this app's page in system settings instead, the only place left to grant it.
     */
    val audioBlocked: Boolean get() = audioBlockedState.value

    /** Asks for what is missing; for a tap, so it may open system settings when asking cannot work. */
    fun request() = onRequest()
}

/**
 * Tracks the audio and notification permissions, re-checking on resume so a grant made in system
 * settings is picked up without a restart.
 *
 * The manifest declared both permissions but nothing ever asked for them — local music came back
 * empty and the playback notification never appeared.
 *
 * A refusal used to leave nothing to tap: once Android stops showing the dialog, launching the
 * request returns "denied" at once with nothing on screen. That case is recognised — refused,
 * no rationale due, and the activity never paused for a dialog — and a tap then goes to this app's
 * system settings page. Never on [requestOnStart]: being thrown into settings at launch, unasked,
 * would be worse than the empty library.
 *
 * @param onAudioGranted invoked when audio access is available; used to kick off the library scan.
 * @param requestOnStart ask immediately. False for the welcome flow, which prompts on a button so
 *   the user has been told what the permission is for before the system dialog appears.
 * @param askNotifications also ask for notifications. False where the prompt is explained as being
 *   about music files alone, so a second, unexplained dialog does not ride along with it.
 */
@Composable
fun rememberPermissionGate(
    onAudioGranted: suspend () -> Unit = {},
    requestOnStart: Boolean = true,
    askNotifications: Boolean = true
): PermissionGate {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasAudioState = remember { mutableStateOf(context.hasAudioPermission()) }
    var hasAudio by hasAudioState
    val audioBlockedState = remember { mutableStateOf(false) }
    var audioBlocked by audioBlockedState
    // Plain holders, not snapshot state: read only in the launcher's callback, never in composition.
    val userAsked = remember { booleanArrayOf(false) }
    val pausedSinceLaunch = remember { booleanArrayOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudio = context.hasAudioPermission()
        // An empty answer is a request cut short (the activity was recreated), not a refusal.
        if (!hasAudio && AUDIO_PERMISSION in results && activity != null) {
            val rationaleDue = ActivityCompat.shouldShowRequestPermissionRationale(activity, AUDIO_PERMISSION)
            audioBlocked = !rationaleDue
            if (audioBlocked && userAsked[0] && !pausedSinceLaunch[0]) context.openAppSettings()
        }
    }

    val ask: (Boolean) -> Unit = { byUser ->
        val missing = buildList {
            if (!context.hasAudioPermission()) add(AUDIO_PERMISSION)
            if (askNotifications) NOTIFICATION_PERMISSION?.takeIf { !context.hasPermission(it) }?.let(::add)
        }
        when {
            byUser && audioBlocked && AUDIO_PERMISSION in missing -> context.openAppSettings()
            missing.isNotEmpty() -> {
                userAsked[0] = byUser
                pausedSinceLaunch[0] = false
                launcher.launch(missing.toTypedArray())
            }
        }
    }

    LaunchedEffect(requestOnStart) {
        if (requestOnStart) ask(false)
    }

    // The permission dialog is an activity of its own, so showing it pauses this one. No pause
    // between launching and the answer means Android answered without asking anyone.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) pausedSinceLaunch[0] = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasAudio = context.hasAudioPermission()
            if (hasAudio) audioBlocked = false
        }
    }

    LaunchedEffect(hasAudio) {
        if (hasAudio) onAudioGranted()
    }

    return remember(hasAudioState) { PermissionGate(hasAudioState, audioBlockedState) { ask(true) } }
}
