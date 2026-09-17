package com.wander.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wander.android.R

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
    // The dialog used to close itself the moment pairing succeeded, on the reasoning that the row
    // behind it reported the connection. That row reported the same thing either way, so a
    // successful pairing was confirmed nowhere at all. It stays open and says so; the user closes it.
    if (dialogs.showAgroDialog) {
        AgroPairingDialog(
            state = state.agroPairing,
            defaultServer = viewModel.agroDefaultServer,
            onPair = viewModel::pairAgro,
            onSignUp = viewModel::signUpAgro,
            onRecheck = viewModel::refreshAgroConnection,
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
            title = stringResource(R.string.settings_sign_out_navidrome),
            message = stringResource(R.string.settings_server_address_username_password_erased),
            confirmLabel = stringResource(R.string.common_sign_out),
            onConfirm = viewModel::disconnectNavidrome,
            onDismiss = { dialogs.confirmNavidromeSignOut = false }
        )
    }

    if (dialogs.confirmYouTubeSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_youtube_music),
            message = stringResource(R.string.settings_library_likes_stop_syncing_until),
            confirmLabel = stringResource(R.string.common_sign_out),
            onConfirm = viewModel::disconnectYouTube,
            onDismiss = { dialogs.confirmYouTubeSignOut = false }
        )
    }

    if (dialogs.confirmAgroUnpair) {
        ConfirmDialog(
            title = stringResource(R.string.settings_unpair_from_agro),
            message = stringResource(R.string.settings_device_stops_appearing_other_devices),
            confirmLabel = stringResource(R.string.settings_unpair),
            onConfirm = viewModel::disconnectAgro,
            onDismiss = { dialogs.confirmAgroUnpair = false }
        )
    }

    if (dialogs.confirmForgetEverything) {
        ConfirmDialog(
            title = stringResource(R.string.settings_forget_all_credentials_2),
            message = stringResource(R.string.settings_will_sign_out_every_music),
            confirmLabel = stringResource(R.string.settings_forget_all),
            onConfirm = {
                viewModel.forgetEverything()
                dialogs.confirmForgetEverything = false
            },
            onDismiss = { dialogs.confirmForgetEverything = false }
        )
    }
}
