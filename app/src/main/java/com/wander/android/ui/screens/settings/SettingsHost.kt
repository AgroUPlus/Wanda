package com.wander.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.agro.AgroSessionViewModel

/**
 * Everything a settings page needs, assembled once.
 *
 * This is the plumbing that used to sit at the top of [SettingsScreen] when that screen *was* the
 * settings — the ViewModel, the launchers, the confirmation dialogs, and the [SettingsActions] bound
 * to them. Now that the settings are a hub of pages, the pages need it and the hub does not, so it
 * lives here rather than in either of them.
 *
 * [SettingsDialogs] is rendered by this function: every dialog in it confirms something
 * irreversible, and a page that forgot to draw them would offer a sign-out that silently did
 * nothing.
 */
@Stable
internal class SettingsHost(
    val state: SettingsUiState,
    val actions: SettingsActions,
    val devices: AgroDevicesState
)

@Composable
internal fun rememberSettingsHost(
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenMergePreview: () -> Unit,
    onOpenFingerprints: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
): SettingsHost {
    val state = rememberSettingsUiState(viewModel)

    // The system's own delete confirmation. It must be launched from an Activity, which is why the
    // ViewModel hands back an IntentSender rather than doing the deletion itself.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* MediaStore removes the rows; the next scan reconciles Room. */ }

    // Same singleton repository the resume card reads, so the two never disagree about who is
    // listening. Refreshed on entry rather than polled.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    // The ungated session, so a device row stays resumable after the pop-up card is dismissed.
    val agroSession by agroViewModel.latestSession.collectAsStateWithLifecycle()
    val agroResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    LaunchedEffect(state.agroPaired) { if (state.agroPaired) agroViewModel.refresh() }
    // One check per visit. This is the only thing that notices a revoked token, and the answer
    // cannot change while the screen is not being looked at, so a poll would buy nothing.
    LaunchedEffect(state.agroPaired) {
        viewModel.refreshAgroConnection()
        viewModel.refreshAgroVisibility()
        viewModel.refreshServerTotalTracks()
        viewModel.refreshYouTubeAccount()
    }

    val uriHandler = LocalUriHandler.current
    val pickLocalFolder = rememberLocalFolderPicker(viewModel::setLocalScanFolder)
    val dialogs = rememberSettingsDialogs()
    SettingsDialogs(state = state, dialogs = dialogs, viewModel = viewModel)

    // Remembered rather than rebuilt per recomposition: every one of these is bound to the
    // ViewModel, the dialog flags or the launchers, none of which change while the screen is up.
    // Rebuilding them would hand each page a fresh set of callbacks on every state emission and
    // undo the point of the @Immutable holder.
    val actions = remember(viewModel, dialogs, pickLocalFolder, uriHandler) {
        SettingsActions(
            onNavidromeLogin = onNavidromeLogin,
            onNavidromeSignOut = { dialogs.confirmNavidromeSignOut = true },
            onYouTubeLogin = onYouTubeLogin,
            onYouTubeSignOut = { dialogs.confirmYouTubeSignOut = true },
            onRescanLocal = viewModel::rescanLocalLibrary,
            onPickLocalFolder = pickLocalFolder.takeIf { supportsFolderScan },
            onAgroPair = { dialogs.showAgroDialog = true },
            onAgroUnpair = { dialogs.confirmAgroUnpair = true },
            onSyncSettingsChange = viewModel::setAgroSyncSettings,
            onPopularityChange = viewModel::setPopularityContribution,
            onCatalogTradeChange = viewModel::setCatalogTrade,
            onResumeHandoff = agroViewModel::resume,
            onP2pSyncChange = viewModel::setP2pSync,
            onServerArchiveChange = viewModel::setServerArchive,
            onSyncNow = viewModel::syncLibraryNow,
            onReviewDeletions = {
                viewModel.buildDeleteRequest { sender ->
                    sender?.let {
                        deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
                    }
                }
            },
            onMonetChange = viewModel::setMonetDynamic,
            onAmoledChange = viewModel::setAmoledBlack,
            onImmersivePlayerChange = viewModel::setImmersivePlayer,
            onCoverArtThemeChange = viewModel::setCoverArtThemeEnabled,
            onOfflineChange = viewModel::setOfflineMode,
            onPreloadNextChange = viewModel::setPreloadNextEnabled,
            onIndexOnMobileDataChange = viewModel::setIndexOnMobileDataEnabled,
            onMeasuringPausedChange = viewModel::setMeasuringPaused,
            onDownloadingPausedChange = viewModel::setDownloadingPaused,
            onDownloadLiked = viewModel::downloadLikedNow,
            onIndexFingerprints = viewModel::indexFingerprintsNow,
            onOpenFingerprints = onOpenFingerprints,
            onClearCache = viewModel::clearCache,
            onOpenImport = onOpenImport,
            onEditShareDomain = { dialogs.showShareDomainDialog = true },
            onIncognitoChange = viewModel::setIncognito,
            onVisibilityChange = viewModel::setAgroVisibility,
            onProxyChange = viewModel::setAgroProxyEnabled,
            onForgetEverything = { dialogs.confirmForgetEverything = true },
            onArtistReleaseNotificationsChange = viewModel::setArtistReleaseNotificationEnabled,
            onAutoUpdateCheckChange = viewModel::setAutoUpdateCheckEnabled,
            onCheckForUpdate = viewModel::checkForUpdate,
            onOpenUrl = uriHandler::openUri,
            onOpenMergePreview = onOpenMergePreview
        )
    }

    val devices = AgroDevicesState(
        devices = agroDevices,
        handoff = agroSession,
        isResuming = agroResuming
    )

    return remember(state, actions, devices) { SettingsHost(state, actions, devices) }
}
