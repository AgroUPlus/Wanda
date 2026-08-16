package com.wander.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Which settings dialog, if any, is open.
 *
 * Every one of these confirms something irreversible — a sign-out, an unpair, a wipe — so none of
 * them may happen on a single stray tap. `rememberSaveable` because a rotation mid-confirmation
 * should not silently cancel the question.
 */
@Stable
internal class SettingsDialogState(
    agro: MutableState<Boolean>,
    share: MutableState<Boolean>,
    navidrome: MutableState<Boolean>,
    youTube: MutableState<Boolean>,
    unpair: MutableState<Boolean>,
    forget: MutableState<Boolean>
) {
    var showAgroDialog by agro
    var showShareDomainDialog by share
    var confirmNavidromeSignOut by navidrome
    var confirmYouTubeSignOut by youTube
    var confirmAgroUnpair by unpair
    var confirmForgetEverything by forget
}

/**
 * The flags are saved individually rather than the holder as a whole: the holder is not Parcelable,
 * and six booleans restore perfectly well on their own.
 */
@Composable
internal fun rememberSettingsDialogs(): SettingsDialogState = SettingsDialogState(
    agro = rememberSaveable { mutableStateOf(false) },
    share = rememberSaveable { mutableStateOf(false) },
    navidrome = rememberSaveable { mutableStateOf(false) },
    youTube = rememberSaveable { mutableStateOf(false) },
    unpair = rememberSaveable { mutableStateOf(false) },
    forget = rememberSaveable { mutableStateOf(false) }
)

@Composable
internal fun SettingsDialogs(
    state: SettingsUiState,
    dialogs: SettingsDialogState,
    viewModel: SettingsViewModel
) {
    // Pairing succeeded: the row behind this now reports the connection, so the dialog has nothing
    // left to say.
    if (dialogs.showAgroDialog && state.agroPairing is AgroPairingState.Paired) {
        dialogs.showAgroDialog = false
    }

    if (dialogs.showAgroDialog) {
        AgroPairingDialog(
            state = state.agroPairing,
            onPair = viewModel::pairAgro,
            onDismiss = {
                dialogs.showAgroDialog = false
                viewModel.resetAgroPairing()
            }
        )
    }

    if (dialogs.showShareDomainDialog) {
        ShareDomainDialog(
            current = state.shareDomain,
            onSave = { domain ->
                viewModel.setShareDomain(domain)
                dialogs.showShareDomainDialog = false
            },
            onDismiss = { dialogs.showShareDomainDialog = false }
        )
    }

    if (dialogs.confirmNavidromeSignOut) {
        ConfirmDialog(
            title = "Sign out of Navidrome?",
            message = "The server address, username and password are erased from this device, " +
                "and your library stops syncing. Anything already downloaded stays playable.",
            confirmLabel = "Sign out",
            onConfirm = viewModel::disconnectNavidrome,
            onDismiss = { dialogs.confirmNavidromeSignOut = false }
        )
    }

    if (dialogs.confirmYouTubeSignOut) {
        ConfirmDialog(
            title = "Sign out of YouTube Music?",
            message = "Your library and likes stop syncing until you sign in again. " +
                "Search keeps working signed out.",
            confirmLabel = "Sign out",
            onConfirm = viewModel::disconnectYouTube,
            onDismiss = { dialogs.confirmYouTubeSignOut = false }
        )
    }

    if (dialogs.confirmAgroUnpair) {
        ConfirmDialog(
            title = "Unpair from Agro?",
            message = "This device stops appearing to your other devices and can no longer pick " +
                "up their sessions. You will need the server address and passphrase to pair again.",
            confirmLabel = "Unpair",
            onConfirm = viewModel::disconnectAgro,
            onDismiss = { dialogs.confirmAgroUnpair = false }
        )
    }

    if (dialogs.confirmForgetEverything) {
        ConfirmDialog(
            title = "Forget all credentials?",
            message = "This will sign out of every music source and erase all stored passwords, " +
                "API keys and login tokens from this device. Downloaded tracks remain on disk.",
            confirmLabel = "Forget all",
            onConfirm = {
                viewModel.forgetEverything()
                dialogs.confirmForgetEverything = false
            },
            onDismiss = { dialogs.confirmForgetEverything = false }
        )
    }
}
