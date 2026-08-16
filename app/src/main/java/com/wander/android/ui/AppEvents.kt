package com.wander.android.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.NetworkStateDialog
import com.wander.android.ui.components.launchShareSheet

/**
 * Everything the app has to say to the user that is not part of a screen.
 *
 * Collected in one place, at the shell, for two reasons. A share link is minted from four different
 * screens and turning one into a share sheet needs an Activity, which no ViewModel may hold; and a
 * failure worth a snackbar can come from playback, from a library write, from sharing or from sync,
 * none of which is tied to whatever is on screen when it happens.
 *
 * Split out of `WanderApp` for length — the shell was well past what one file should hold.
 */
@Composable
internal fun AppEvents(
    viewModel: WanderAppViewModel,
    playerConnection: PlayerConnection,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(playerConnection) {
        playerConnection.errors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.writeErrors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.shareLinks.collect { link -> context.launchShareSheet(link) }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareErrors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.syncErrors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    // A dialog rather than a snackbar: it asks a question, and the answer changes a setting.
    // Raised at the shell because the network can drop on any screen and the answer applies to all
    // of them.
    val networkPrompt by viewModel.networkPrompt.collectAsStateWithLifecycle()
    networkPrompt?.let { prompt ->
        NetworkStateDialog(
            prompt = prompt,
            onConfirm = { viewModel.acceptNetworkPrompt(prompt) },
            onDismiss = { viewModel.dismissNetworkPrompt(prompt) }
        )
    }
}
