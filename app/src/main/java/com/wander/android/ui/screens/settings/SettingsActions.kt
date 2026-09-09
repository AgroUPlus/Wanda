package com.wander.android.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroVisibility

/**
 * Everything the settings tabs can do, opposite [SettingsUiState]'s everything they can read.
 *
 * Split from the state rather than merged with it because the two change on different clocks: the
 * state is re-collected whenever a flow emits, while these are bound once to the ViewModel and the
 * screen's dialogs. Holding them apart keeps a value change from allocating a fresh set of
 * callbacks, and keeps a tab's signature to "what it shows" and "what it can do".
 */
@Immutable
internal data class SettingsActions(
    // Connections
    val onNavidromeLogin: () -> Unit,
    val onNavidromeSignOut: () -> Unit,
    val onYouTubeLogin: () -> Unit,
    val onYouTubeSignOut: () -> Unit,
    val onRescanLocal: () -> Unit,
    /** Null when this device is too old to narrow the scan — see `supportsFolderScan`. */
    val onPickLocalFolder: (() -> Unit)?,
    // Agro pairing and sync
    val onAgroPair: () -> Unit,
    val onAgroUnpair: () -> Unit,
    val onSyncSettingsChange: (Boolean) -> Unit,
    val onPopularityChange: (Boolean) -> Unit,
    val onCatalogTradeChange: (Boolean) -> Unit,
    val onResumeHandoff: (AgroHandoffState) -> Unit,
    val onP2pSyncChange: (Boolean) -> Unit,
    val onServerArchiveChange: (Boolean) -> Unit,
    val onSyncNow: () -> Unit,
    val onReviewDeletions: () -> Unit,
    // Appearance
    val onMonetChange: (Boolean) -> Unit,
    val onAmoledChange: (Boolean) -> Unit,
    // Playback and storage
    val onOfflineChange: (Boolean) -> Unit,
    val onPreloadNextChange: (Boolean) -> Unit,
    val onIndexOnMobileDataChange: (Boolean) -> Unit,
    val onMeasuringPausedChange: (Boolean) -> Unit,
    val onDownloadingPausedChange: (Boolean) -> Unit,
    val onDownloadLiked: () -> Unit,
    val onIndexFingerprints: () -> Unit,
    val onOpenFingerprints: () -> Unit,
    val onClearCache: () -> Unit,
    // External
    val onOpenImport: () -> Unit,
    val onEditShareDomain: () -> Unit,
    // Privacy
    val onIncognitoChange: (Boolean) -> Unit,
    val onVisibilityChange: (AgroVisibility) -> Unit,
    val onProxyChange: (Boolean) -> Unit,
    val onForgetEverything: () -> Unit,
    // About
    val onReleaseNotificationsChange: (Boolean) -> Unit,
    val onAutoUpdateCheckChange: (Boolean) -> Unit,
    val onCheckForUpdate: () -> Unit,
    /**
     * Opens a link in the browser. The release-notes row and the credits rows used to take two
     * separate callbacks that were the same `uriHandler::openUri` at the only call site.
     */
    val onOpenUrl: (String) -> Unit,
    val onOpenMergePreview: () -> Unit
)
