package com.wander.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.update.UpdateCheckResult
import com.wander.android.core.update.UpdateChecker
import com.wander.android.data.repository.InstantRadioRepository
import com.wander.android.data.repository.FetchProgress
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.SyncOfferArtwork
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.core.audio.fingerprint.FingerprintIndexing
import com.wander.android.data.repository.SearchQueryHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.PeerReachability
import com.wander.android.data.sources.agro.SyncRoute
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
    private val musicRepository: MusicRepository,
    shareRepository: ShareRepository,
    private val librarySync: LibrarySyncRepository,
    private val syncOfferArtwork: SyncOfferArtwork,
    private val peerReachability: PeerReachability,
    playlistWriter: PlaylistWriteRepository,
    private val sessionApi: AgroSessionApi,
    private val secureStorage: SecureStorage,
    private val deepLinkRouter: DeepLinkRouter,
    connectivity: ConnectivityObserver,
    private val updateChecker: UpdateChecker,
    private val instantRadio: InstantRadioRepository,
    private val playerConnection: PlayerConnection,
    private val searchQueryHolder: SearchQueryHolder,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    /**
     * The dock's search text.
     *
     * Owned by a singleton rather than by `SearchViewModel`, because the field is in the dock and
     * the dock outlives the Search destination — see [SearchQueryHolder].
     */
    val searchQuery: StateFlow<String> = searchQueryHolder.query

    fun setSearchQuery(value: String) = searchQueryHolder.set(value)

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

    /** Covers for the first few offered tracks, so the card can show what is on offer. */
    private val _syncCovers = MutableStateFlow<List<String>>(emptyList())
    val syncCovers: StateFlow<List<String>> = _syncCovers.asStateFlow()

    /**
     * The route the *next* fetch would actually take, measured rather than assumed.
     *
     * Null until it has been worked out. See [PeerReachability]: a peer that publishes a LAN
     * address is not necessarily reachable from here, and saying "Direct Wi-Fi" when it is not is
     * a promise the transfer then breaks.
     */
    private val _offerRoute = MutableStateFlow<SyncRoute?>(null)
    val offerRoute: StateFlow<SyncRoute?> = _offerRoute.asStateFlow()

    /** Which tracks are done, which is in flight, and how it is travelling. */
    private val _fetchProgress = MutableStateFlow(FetchProgress())
    val fetchProgress: StateFlow<FetchProgress> = _fetchProgress.asStateFlow()

    /** Whether the full list is open. The card is a summary; this is the detail behind it. */
    private val _syncDetailsOpen = MutableStateFlow(false)
    val syncDetailsOpen: StateFlow<Boolean> = _syncDetailsOpen.asStateFlow()

    fun openSyncDetails() { _syncDetailsOpen.value = true }

    fun closeSyncDetails() { _syncDetailsOpen.value = false }

    /**
     * Dismissed for this visit only, mirroring how the resume card behaves: an offer declined now
     * should be offerable again next time the app is opened, not suppressed forever.
     */
    private var dismissedOffer = false

    /** Asks what this device is missing. Cheap, metadata only. */
    fun refreshSyncOffer() {
        if (dismissedOffer || !librarySync.isEnabled) return
        viewModelScope.launch {
            // Deletions first. A file that has left this device has to be un-reported *before* the
            // server is asked what is missing, or the answer is computed from an index that still
            // believes a copy exists here — which is why a deleted track was never offered back.
            librarySync.flushPendingForget()
            val offered = librarySync.missingHere().getOrDefault(emptyList())
            _syncOffer.value = offered
            _syncCovers.value = syncOfferArtwork.covers(offered)
            _offerRoute.value = routeFor(offered)
        }
    }

    /**
     * How the tracks on offer would travel, decided by trying the local address rather than by
     * trusting that one was published.
     */
    private suspend fun routeFor(offered: List<MissingTrack>): SyncRoute? {
        val source = offered.firstOrNull()?.peerSources?.firstOrNull() ?: return null
        return when {
            peerReachability.canReach(source.lanAddress) -> SyncRoute.DIRECT
            source.isServerArchive -> SyncRoute.ARCHIVE
            else -> SyncRoute.RELAY
        }
    }

    fun acceptSyncOffer() {
        val tracks = _syncOffer.value
        if (tracks.isEmpty() || _isFetching.value) return
        viewModelScope.launch {
            _isFetching.value = true
            librarySync.fetchMissing(tracks) { _fetchProgress.value = it }
                .onSuccess { count ->
                    _syncOffer.value = emptyList()
                    _syncCovers.value = emptyList()
                    _syncDetailsOpen.value = false
                    _offerRoute.value = null
                    // Says so when it is done. A card that simply disappears leaves the user to
                    // guess whether the transfer finished or was dismissed.
                    if (count > 0) {
                        _writeErrors.tryEmit(
                            if (count == 1) "1 track added to this device"
                            else "$count tracks added to this device"
                        )
                    }
                }
                .onFailure { _writeErrors.tryEmit(it.message ?: "Couldn't fetch those tracks.") }
            _isFetching.value = false
            _fetchProgress.value = FetchProgress()
        }
    }

    fun dismissSyncOffer() {
        dismissedOffer = true
        _syncOffer.value = emptyList()
        _syncCovers.value = emptyList()
        _syncDetailsOpen.value = false
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
    init {
        // Repairs likes made before a like meant the recording rather than the row. Idempotent and
        // additive — it can only spread an existing like to other copies of the same performance,
        // never remove one — so it is safe to run on every launch rather than needing a flag.
        viewModelScope.launch { musicRepository.unifySplitLikes() }
    }

    fun onAudioPermissionGranted() {
        viewModelScope.launch {
            localSource.refresh()
            // The scan is what discovers the files, so the fingerprint index can only usefully be
            // asked for afterwards. `KEEP` inside means the repeated calls this makes across
            // launches join one run rather than restarting it. It no longer waits for a charger —
            // a streamed library is only reachable while the app is in use — so asking here can
            // actually lead to work being done.
            FingerprintIndexing.enqueue(
                context,
                allowMobileData = secureStorage.isIndexOnMobileDataEnabled.value
            )
        }
    }
}

/** Which way the network just turned, and so what there is to offer the user. */
enum class NetworkPrompt { GO_OFFLINE, GO_ONLINE }

/**
 * How long the network has to hold a state before the app believes it. A lift, a tunnel or a
 * Wi-Fi/mobile handover all flap, and each flap would otherwise be a dialog.
 */
private const val NetworkSettleMs = 5_000L
