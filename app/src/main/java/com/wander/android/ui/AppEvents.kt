package com.wander.android.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.ConfirmDialog
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
    // Resolved at display time, so a message follows the app's language — see PlaybackMessage.
    val context = LocalContext.current
    LaunchedEffect(playerConnection, context) {
        playerConnection.errors.collect { message ->
            snackbarHostState.showSnackbar(message.resolve(context), withDismissAction = true)
        }
    }

    LaunchedEffect(playerConnection, context) {
        playerConnection.notices.collect { notice ->
            val undo = notice.undo
            if (undo == null) {
                snackbarHostState.showSnackbar(notice.message.resolve(context))
                return@collect
            }
            // Long, not the Indefinite a snackbar with an action defaults to: an offer to undo
            // should outlast a glance, but not sit there until it is dismissed by hand.
            val result = snackbarHostState.showSnackbar(
                message = notice.message.resolve(context),
                actionLabel = context.getString(R.string.common_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.writeErrors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.shareLinks.collect { link -> context.launchShareSheet(link) }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareErrors.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.playlistMessages.collect { message -> snackbarHostState.showSnackbar(message) }
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

    // Pairing and friend links can be fired by any page or app, so they wait here for a yes.
    val linkConfirmation by viewModel.pendingLinkConfirmation.collectAsStateWithLifecycle()
    linkConfirmation?.let { request ->
        ConfirmDialog(
            title = stringResource(request.title),
            message = stringResource(request.message, *request.messageArgs.toTypedArray()),
            confirmLabel = stringResource(request.confirmLabel),
            onConfirm = { viewModel.confirmLink(request) },
            onDismiss = { viewModel.dismissLink(request) }
        )
    }
}
