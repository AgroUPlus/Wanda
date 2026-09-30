package com.wander.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.audio.fingerprint.FingerprintIndexing
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.core.update.UpdateChecker
import com.wander.android.data.replay.ReplayAvailability
import com.wander.android.data.repository.FetchProgress
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.data.repository.FingerprintStatusRepository
import com.wander.android.data.repository.InstantRadioRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.SearchQueryHolder
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.agro.SyncRoute
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.ui.navigation.DeepLinkRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WanderAppViewModel @Inject constructor(
    private val localSource: LocalMusicSource,
    private val musicRepository: MusicRepository,
    shareRepository: ShareRepository,
    private val syncCoordinator: SyncOfferCoordinator,
    private val networkCoordinator: NetworkPromptCoordinator,
    private val secureStorage: SecureStorage,
    private val deepLinkRouter: DeepLinkRouter,
    private val updateChecker: UpdateChecker,
    private val instantRadio: InstantRadioRepository,
    private val playerConnection: PlayerConnection,
    private val searchQueryHolder: SearchQueryHolder,
    fingerprintStatuses: FingerprintStatusRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    /** Whether Home/Library's background should wash toward the playing cover's colour. */
    val isCoverArtThemeEnabled: StateFlow<Boolean> = secureStorage.isCoverArtThemeEnabled

    /** True black pins the darkest surfaces regardless — see `CoverTintedTheme`'s `amoled` param. */
    val isAmoledBlack: StateFlow<Boolean> = secureStorage.isAmoledBlack

    /** Whether back gestures blur what they reveal — see `LocalBackBlurEnabled`. */
    val isBackBlurEnabled: StateFlow<Boolean> = secureStorage.isBackBlurEnabled

    /** Whether the track on the player's cover has been measured. */
    val playingFingerprintStatus: StateFlow<FingerprintStatus> =
        combine(
            playerConnection.state,
            fingerprintStatuses.statuses()
        ) { playback, statuses ->
            playback.currentTrack?.id?.let { statuses[it] } ?: FingerprintStatus.MISSING
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FingerprintStatus.MISSING
        )

    /** The dock's search text. */
    val searchQuery: StateFlow<String> = searchQueryHolder.query

    fun setSearchQuery(value: String) = searchQueryHolder.set(value)

    val isImmersivePlayer: StateFlow<Boolean> = secureStorage.isImmersivePlayer

    /** The instant-radio button, hoisted to the shell. */
    private val _isStartingRadio = MutableStateFlow(false)
    val isStartingRadio: StateFlow<Boolean> = _isStartingRadio.asStateFlow()

    /** Starts a station with nothing to go on — no seed, no chosen playlist. */
    fun startInstantRadio() {
        if (_isStartingRadio.value) return
        _isStartingRadio.value = true
        viewModelScope.launch {
            val station = instantRadio.buildStation()
            _isStartingRadio.value = false
            if (station.isEmpty()) {
                playerConnection.notifyNoStation()
                return@launch
            }
            playerConnection.play(station)
            playerConnection.setRadioMode(true)
        }
    }

    /** Routes asked for from outside the composition — a tapped notification. */
    val deepLinkRoutes = deepLinkRouter.routes

    fun consumeDeepLink() = deepLinkRouter.consume()

    /** Library writes that failed to reach their backend — shown as a snackbar, not swallowed. */
    val writeErrors = musicRepository.writeErrors

    /** Share links minted anywhere in the app. */
    val shareLinks = shareRepository.links

    /** A share the backend refused — usually sharing disabled server-side. */
    val shareErrors = shareRepository.errors

    // ── Library sync offers (delegated to SyncOfferCoordinator) ─────────────────────────────

    val syncOffer: StateFlow<List<MissingTrack>> = syncCoordinator.syncOffer
    val isFetchingSync: StateFlow<Boolean> = syncCoordinator.isFetchingSync
    val syncCovers: StateFlow<List<String>> = syncCoordinator.syncCovers
    val offerRoute: StateFlow<SyncRoute?> = syncCoordinator.offerRoute
    val fetchProgress: StateFlow<FetchProgress> = syncCoordinator.fetchProgress
    val syncDetailsOpen: StateFlow<Boolean> = syncCoordinator.syncDetailsOpen

    fun openSyncDetails() = syncCoordinator.openSyncDetails()
    fun closeSyncDetails() = syncCoordinator.closeSyncDetails()
    fun refreshSyncOffer() = syncCoordinator.refreshSyncOffer(viewModelScope)
    fun acceptSyncOffer() = syncCoordinator.acceptSyncOffer(viewModelScope)
    fun dismissSyncOffer() = syncCoordinator.dismissSyncOffer()
    fun refreshShareSettings() = syncCoordinator.refreshShareSettings(viewModelScope)
    val syncErrors: SharedFlow<String> = syncCoordinator.syncErrors(viewModelScope)

    // ── Network transitions (delegated to NetworkPromptCoordinator) ─────────────────────────

    val offlinePlayback: StateFlow<Boolean> =
        networkCoordinator.createOfflinePlaybackFlow(viewModelScope)

    val networkPrompt: StateFlow<NetworkPrompt?> =
        networkCoordinator.createNetworkPromptFlow(viewModelScope)

    fun acceptNetworkPrompt(prompt: NetworkPrompt) = networkCoordinator.acceptPrompt(prompt)
    fun dismissNetworkPrompt(prompt: NetworkPrompt) = networkCoordinator.dismissPrompt(prompt)

    /** Decides whether the app opens on the welcome flow or straight into the library. */
    val hasCompletedSetup: StateFlow<Boolean> = secureStorage.hasCompletedSetup

    // ── Update check on launch ──────────────────────────────────────────────────────────────

    private val _launchUpdateAvailable = MutableStateFlow<UpdateCheckResult.UpdateAvailable?>(null)
    val launchUpdateAvailable: StateFlow<UpdateCheckResult.UpdateAvailable?> =
        _launchUpdateAvailable.asStateFlow()

    private var hasCheckedThisLaunch = false

    fun checkForUpdateOnLaunch() {
        if (hasCheckedThisLaunch || !secureStorage.isAutoUpdateCheckEnabled.value) return
        hasCheckedThisLaunch = true
        viewModelScope.launch {
            (updateChecker.checkForUpdate() as? UpdateCheckResult.UpdateAvailable)?.let {
                _launchUpdateAvailable.value = it
            }
        }
    }

    fun dismissLaunchUpdate() {
        _launchUpdateAvailable.value = null
    }

    // ── Agro Replay ─────────────────────────────────────────────────────────────────────────

    private val _replayOffer = MutableStateFlow(
        ReplayAvailability.shouldOffer(LocalDate.now(), secureStorage.lastSeenReplayYear)
    )
    val replayOffer: StateFlow<Int?> = _replayOffer.asStateFlow()

    fun dismissReplayOffer() {
        _replayOffer.value?.let { year ->
            if (year > secureStorage.lastSeenReplayYear) {
                secureStorage.lastSeenReplayYear = year
            }
        }
        _replayOffer.value = null
    }

    init {
        viewModelScope.launch { musicRepository.unifySplitLikes() }
    }

    fun onAudioPermissionGranted() {
        viewModelScope.launch {
            localSource.refresh()
            FingerprintIndexing.enqueue(
                context,
                allowMobileData = secureStorage.isIndexOnMobileDataEnabled.value
            )
        }
    }
}
