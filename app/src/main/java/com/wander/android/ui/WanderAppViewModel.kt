package com.wander.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.core.update.UpdateChecker
import com.wander.android.data.repository.InstantRadioRepository
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.ui.navigation.DeepLinkRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WanderAppViewModel @Inject constructor(
    private val localSource: LocalMusicSource,
    musicRepository: MusicRepository,
    shareRepository: ShareRepository,
    private val librarySync: LibrarySyncRepository,
    playlistWriter: PlaylistWriteRepository,
    private val sessionApi: AgroSessionApi,
    private val secureStorage: SecureStorage,
    private val deepLinkRouter: DeepLinkRouter,
    connectivity: ConnectivityObserver,
    private val updateChecker: UpdateChecker,
    private val instantRadio: InstantRadioRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    /**
     * The instant-radio button, hoisted to the shell.
     *
     * It used to live in `HomeScreen`, which put it inside the nav host — and the player sheet is
     * drawn after the whole nav host, so the button lost to the docked strip whenever the two came
     * near each other regardless of elevation. Owning it here is the only way it can sit *above*
     * the mini player rather than merely beside it.
     *
     * The state has to live at this level too, or the shell would be reading a different
     * `HomeViewModel` instance than the screen does — one per nav backstack entry.
     */
    private val _isStartingRadio = MutableStateFlow(false)
    val isStartingRadio: StateFlow<Boolean> = _isStartingRadio.asStateFlow()

    /**
     * Whether Home's feed is moving, reported up by the screen.
     *
     * A button pinned over a scrolling list is in the way of the thing being scrolled, and the
     * shell has no other way to know — the list state belongs to the screen.
     */
    private val _homeScrolling = MutableStateFlow(false)
    val homeScrolling: StateFlow<Boolean> = _homeScrolling.asStateFlow()

    fun setHomeScrolling(scrolling: Boolean) {
        _homeScrolling.value = scrolling
    }

    /**
     * Starts a station with nothing to go on — no seed, no chosen playlist.
     *
     * Radio mode goes on with it, so the station keeps topping itself up instead of ending forty
     * tracks later. An empty result is reported rather than silently ignored: a library with no
     * plays and no likes has said nothing about what its owner wants to hear, and a button that
     * quietly does nothing reads as broken.
     */
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

    /**
     * Share links minted anywhere in the app. Collected in one place because turning a URL into a
     * share sheet needs an Activity, which no ViewModel should hold.
     */
    val shareLinks = shareRepository.links

    /** A share the backend refused — usually sharing disabled server-side. */
    val shareErrors = shareRepository.errors

    // ── Library sync offers ─────────────────────────────────────────────────────────────────

    private val _syncOffer = MutableStateFlow<List<MissingTrack>>(emptyList())
    val syncOffer: StateFlow<List<MissingTrack>> = _syncOffer.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetchingSync: StateFlow<Boolean> = _isFetching.asStateFlow()

    /**
     * Dismissed for this visit only, mirroring how the resume card behaves: an offer declined now
     * should be offerable again next time the app is opened, not suppressed forever.
     */
    private var dismissedOffer = false

    /** Asks what this device is missing. Cheap, metadata only. */
    fun refreshSyncOffer() {
        if (dismissedOffer || !librarySync.isEnabled) return
        viewModelScope.launch {
            _syncOffer.value = librarySync.missingHere().getOrDefault(emptyList())
        }
    }

    fun acceptSyncOffer() {
        val tracks = _syncOffer.value
        if (tracks.isEmpty() || _isFetching.value) return
        viewModelScope.launch {
            _isFetching.value = true
            librarySync.fetchMissing(tracks)
                .onSuccess { _syncOffer.value = emptyList() }
                .onFailure { _writeErrors.tryEmit(it.message ?: "Couldn't fetch those tracks.") }
            _isFetching.value = false
        }
    }

    fun dismissSyncOffer() {
        dismissedOffer = true
        _syncOffer.value = emptyList()
    }

    /**
     * Picks up the share-link domain a paired Agro server publishes, so it is set once for the
     * whole fleet rather than typed into every player.
     *
     * Silent on every failure. This is a convenience that Agro *may* provide: an unpaired server,
     * an unreachable one, or one too old to know the field all leave the locally configured
     * domain — or no rewriting at all — exactly as it was. Sharing never depends on Agro.
     */
    fun refreshShareSettings() {
        if (!secureStorage.agroConfigured.value) return
        viewModelScope.launch {
            val settings = sessionApi.syncedSettings().getOrNull() ?: return@launch
            val domain = settings.shareDomain.orEmpty().takeIf { settings.shareEnabled }.orEmpty()
            secureStorage.setAgroShareSettings(domain, settings.shareHosts.orEmpty())
        }
    }

    /** Local failures worth a snackbar, alongside the repository's own. */
    private val _writeErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Everything the user should hear about, from here and from the sync repository.
     *
     * The repository's errors were emitted but never collected — the comment above claimed they
     * were included and they were not, so a per-file upload failure said nothing at all and sync
     * simply looked like it had stopped.
     */
    val syncErrors: SharedFlow<String> =
        merge(_writeErrors.asSharedFlow(), librarySync.errors)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 0)

    // ── Network transitions ─────────────────────────────────────────────────────────────────

    private val dismissedPrompt = MutableStateFlow<NetworkPrompt?>(null)

    /**
     * Playback is cut off from the network: offline mode is on, or there is no connection.
     *
     * The same two conditions `MusicRepository.activeSources()` mutes remote sources on, hoisted
     * so the UI can dim what it is about to refuse to play rather than letting the user find out
     * by tapping.
     */
    val offlinePlayback: StateFlow<Boolean> = combine(
        connectivity.isOnline,
        secureStorage.isOfflineMode
    ) { online, offlineMode -> offlineMode || !online }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The prompt to offer when the network state changes, or null when there is nothing to ask.
     *
     * Offline mode was a switch buried in Settings that the user had to remember existed, so
     * losing signal just meant remote sources failing one request at a time. Now the app notices
     * and asks — once per transition, and only when the answer would actually change something:
     * there is no point offering to enable offline mode when it is already on.
     *
     * `drop(1)` skips the flow's seeded value: the state at launch is not a transition, and without
     * it every cold start on a plane would open with a dialog.
     */
    // `debounce` is still a preview API. Opted into deliberately rather than hand-rolling a
    // timer: the alternative is a coroutine and a mutable timestamp for behaviour the operator
    // already has exactly right.
    @OptIn(FlowPreview::class)
    val networkPrompt: StateFlow<NetworkPrompt?> = combine(
        connectivity.isOnline.drop(1).debounce(NetworkSettleMs),
        secureStorage.isOfflineMode,
        dismissedPrompt
    ) { online, offlineMode, dismissed ->
        val prompt = when {
            !online && !offlineMode -> NetworkPrompt.GO_OFFLINE
            online && offlineMode -> NetworkPrompt.GO_ONLINE
            else -> null
        }
        // A declined prompt stays declined until the *other* transition happens, which is a state
        // the user has not answered for yet.
        prompt?.takeIf { it != dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun acceptNetworkPrompt(prompt: NetworkPrompt) {
        secureStorage.setOfflineMode(prompt == NetworkPrompt.GO_OFFLINE)
        dismissedPrompt.value = prompt
    }

    fun dismissNetworkPrompt(prompt: NetworkPrompt) {
        dismissedPrompt.value = prompt
    }

    /** Decides whether the app opens on the welcome flow or straight into the library. */
    val hasCompletedSetup: StateFlow<Boolean> = secureStorage.hasCompletedSetup

    // ── Update check on launch ──────────────────────────────────────────────────────────────

    private val _launchUpdateAvailable = MutableStateFlow<UpdateCheckResult.UpdateAvailable?>(null)
    val launchUpdateAvailable: StateFlow<UpdateCheckResult.UpdateAvailable?> =
        _launchUpdateAvailable.asStateFlow()

    private var hasCheckedThisLaunch = false

    /**
     * Runs at most once per process. Gated on the Settings toggle, which defaults off: this is a
     * network call the user did not ask for, so it only fires when they opted in.
     */
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

    /**
     * Runs once the audio permission is granted. The scan is incremental, so calling it on every
     * cold start costs almost nothing after the first time.
     */
    fun onAudioPermissionGranted() {
        viewModelScope.launch { localSource.refresh() }
    }
}

/** Which way the network just turned, and so what there is to offer the user. */
enum class NetworkPrompt { GO_OFFLINE, GO_ONLINE }

/**
 * How long the network has to hold a state before the app believes it. A lift, a tunnel or a
 * Wi-Fi/mobile handover all flap, and each flap would otherwise be a dialog.
 */
private const val NetworkSettleMs = 5_000L
