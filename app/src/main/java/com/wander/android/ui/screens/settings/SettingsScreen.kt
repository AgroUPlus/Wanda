package com.wander.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.agro.AgroSessionViewModel
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import kotlinx.coroutines.launch

/**
 * Settings, as six tabs rather than one long scroll.
 *
 * The screen itself owns nothing but the state it collects, the confirmation dialogs — every one of
 * which discards credentials, so none of them may fire on a single stray tap — and the tab
 * scaffolding. Each tab's rows live in their own file; see [SettingsTab].
 */
@Composable
internal fun SettingsScreen(
    contentPadding: PaddingValues,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onOpenImport: () -> Unit = {},
    onOpenMergePreview: () -> Unit = {},
    onOpenFingerprints: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
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
    // One check per visit to Settings. This is the only thing that notices a revoked token, and the
    // answer cannot change while the screen is not being looked at, so a poll would buy nothing.
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

    val devices = AgroDevicesState(
        devices = agroDevices,
        handoff = agroSession,
        isResuming = agroResuming
    )

    // Remembered rather than rebuilt per recomposition: every one of these is bound to the
    // ViewModel, the dialog flags or the launchers, none of which change while the screen is up.
    // Rebuilding them would hand each tab a fresh set of callbacks on every state emission and
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
            onReleaseNotificationsChange = viewModel::setReleaseNotificationEnabled,
            onAutoUpdateCheckChange = viewModel::setAutoUpdateCheckEnabled,
            onCheckForUpdate = viewModel::checkForUpdate,
            onOpenUrl = uriHandler::openUri,
            onOpenMergePreview = onOpenMergePreview
        )
    }

    val tabs = SettingsTab.entries
    val pagerState = rememberPagerState(pageCount = tabs::size)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // The same expressive indicator the Library header uses: a pill under the label rather
        // than a bar under the whole tab, so which section you are in reads from its silhouette.
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 12.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        pagerState.currentPage,
                        matchContentSize = true
                    ),
                    width = Dp.Unspecified,
                    height = 3.dp,
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            LazyColumn(
                contentPadding = contentPadding.listInset(),
                modifier = Modifier.fillMaxSize()
            ) {
                when (tabs[page]) {
                    SettingsTab.CONNECTIONS -> connectionsTab(state, actions)
                    SettingsTab.SYNC -> syncTab(state, actions, devices)
                    SettingsTab.APPEARANCE -> appearanceTab(state, actions)
                    SettingsTab.PLAYBACK -> playbackStorageTab(state, actions)
                    SettingsTab.EXTERNAL -> externalTab(state, actions)
                    SettingsTab.PRIVACY -> privacyTab(state, actions)
                    SettingsTab.ABOUT -> aboutTab(state, actions)
                }
            }
        }
    }
}
