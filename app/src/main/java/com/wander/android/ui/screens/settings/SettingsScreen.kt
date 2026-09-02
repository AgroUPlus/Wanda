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
                    SettingsTab.CONNECTIONS -> connectionsTab(
                        navidromeConnected = state.navidrome,
                        navidromeServer = viewModel.navidromeServer,
                        syncedNavidrome = state.syncedNavidrome,
                        youTubeConnected = state.youTube,
                        youTubeAccount = state.youTubeAccount,
                        localReady = state.localReady,
                        onNavidromeLogin = onNavidromeLogin,
                        onNavidromeSignOut = { dialogs.confirmNavidromeSignOut = true },
                        onYouTubeLogin = onYouTubeLogin,
                        onYouTubeSignOut = { dialogs.confirmYouTubeSignOut = true },
                        onRescanLocal = viewModel::rescanLocalLibrary,
                        onPickLocalFolder = pickLocalFolder.takeIf { supportsFolderScan },
                        localFolder = state.localScanFolder
                    )

                    SettingsTab.SYNC -> syncTab(
                        paired = state.agroPaired,
                        connection = state.agroConnection,
                        devicePetname = viewModel.agroDevicePetname,
                        server = viewModel.agroServer,
                        syncSettings = state.agroSyncSettings,
                        onSyncSettingsChange = viewModel::setAgroSyncSettings,
                        onPair = { dialogs.showAgroDialog = true },
                        onUnpair = { dialogs.confirmAgroUnpair = true },
                        devices = agroDevices,
                        handoff = agroSession,
                        isResuming = agroResuming,
                        onResume = agroViewModel::resume,
                        p2pSyncEnabled = state.p2pSync,
                        onP2pSyncChange = viewModel::setP2pSync,
                        serverArchiveEnabled = state.serverArchive,
                        popularityEnabled = state.popularityContribution,
                        onPopularityChange = viewModel::setPopularityContribution,
                        canArchive = state.canArchive,
                        onServerArchiveChange = viewModel::setServerArchive,
                        pendingUploads = state.pendingUploads,
                        syncedTracks = state.syncedTracks,
                        localTracks = state.localTracks,
                        incognito = state.incognito,
                        serverTotalTracks = state.serverTotalTracks,
                        syncProgress = state.syncProgress,
                        filesLandInNavidrome = state.navidrome,
                        onSyncNow = viewModel::syncLibraryNow,
                        onReviewDeletions = {
                            viewModel.buildDeleteRequest { sender ->
                                sender?.let {
                                    deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            }
                        },
                        canDelete = viewModel.canDeleteLocalFiles && state.syncedTracks > 0
                    )

                    SettingsTab.APPEARANCE -> appearanceTab(
                        monet = state.monet,
                        onMonetChange = viewModel::setMonetDynamic,
                        amoled = state.amoled,
                        onAmoledChange = viewModel::setAmoledBlack
                    )

                    SettingsTab.PLAYBACK -> playbackStorageTab(
                        offline = state.offline,
                        preloadNext = state.preloadNext,
                        onPreloadNextChange = viewModel::setPreloadNextEnabled,
                        onOfflineChange = viewModel::setOfflineMode,
                        cacheBytes = state.cacheBytes,
                        onDownloadLiked = viewModel::downloadLikedNow,
                        onIndexFingerprints = viewModel::indexFingerprintsNow,
                        onClearCache = viewModel::clearCache
                    )

                    SettingsTab.EXTERNAL -> externalTab(
                        onOpenImport = onOpenImport,
                        shareDomain = state.shareDomain,
                        agroShareDomain = state.agroShareDomain,
                        onEditDomain = { dialogs.showShareDomainDialog = true },
                        incognito = state.incognito
                    )

                    SettingsTab.PRIVACY -> privacyTab(
                        incognito = state.incognito,
                        onIncognitoChange = viewModel::setIncognito,
                        agroPaired = state.agroPaired,
                        visibility = state.agroVisibility,
                        onVisibilityChange = viewModel::setAgroVisibility,
                        proxyEnabled = state.agroProxyEnabled,
                        onProxyChange = viewModel::setAgroProxyEnabled,
                        onForgetEverything = { dialogs.confirmForgetEverything = true }
                    )

                    SettingsTab.ABOUT -> aboutTab(
                        appVersion = state.appVersion,
                        updateCheck = state.updateCheck,
                        isChecking = state.isCheckingForUpdate,
                        autoUpdateCheck = state.autoUpdateCheckEnabled,
                        releaseNotifications = state.releaseNotificationsEnabled,
                        onReleaseNotificationsChange = viewModel::setReleaseNotificationEnabled,
                        onAutoUpdateCheckChange = viewModel::setAutoUpdateCheckEnabled,
                        onCheckForUpdate = viewModel::checkForUpdate,
                        onOpenRelease = { uriHandler.openUri(it) },
                        onOpenUrl = { uriHandler.openUri(it) },
                        onOpenMergePreview = onOpenMergePreview
                    )
                }
            }
        }
    }
}
