package com.wander.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Checks for updates on launch and manages initial audio permission gates.
 */
@Composable
internal fun WanderAppLaunchGate(
    viewModel: WanderAppViewModel,
    setupDone: Boolean
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.checkForUpdateOnLaunch() }
    val launchUpdate by viewModel.launchUpdateAvailable.collectAsStateWithLifecycle()
    launchUpdate?.let { update ->
        com.wander.android.ui.components.UpdateAvailableDialog(
            update = update,
            onDismiss = viewModel::dismissLaunchUpdate
        )
    }

    com.wander.android.core.permissions.rememberPermissionGate(
        onAudioGranted = viewModel::onAudioPermissionGranted,
        requestOnStart = setupDone
    )
}
