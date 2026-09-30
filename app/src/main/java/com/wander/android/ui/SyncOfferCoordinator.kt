package com.wander.android.ui

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.FetchProgress
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.SyncOfferArtwork
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.agro.PeerReachability
import com.wander.android.data.sources.agro.SyncRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates peer-to-peer and Agro library sync offers, tracking offered tracks, covers,
 * fetch progress, reachability routes, and sync error notifications.
 */
class SyncOfferCoordinator @Inject constructor(
    private val librarySync: LibrarySyncRepository,
    private val syncOfferArtwork: SyncOfferArtwork,
    private val peerReachability: PeerReachability,
    private val sessionApi: AgroSessionApi,
    private val secureStorage: SecureStorage
) {
    private val _syncOffer = MutableStateFlow<List<MissingTrack>>(emptyList())
    val syncOffer: StateFlow<List<MissingTrack>> = _syncOffer.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetchingSync: StateFlow<Boolean> = _isFetching.asStateFlow()

    /** Covers for the first few offered tracks, so the card can show what is on offer. */
    private val _syncCovers = MutableStateFlow<List<String>>(emptyList())
    val syncCovers: StateFlow<List<String>> = _syncCovers.asStateFlow()

    /**
     * The route the *next* fetch would actually take, measured rather than assumed.
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
    fun refreshSyncOffer(scope: CoroutineScope) {
        if (dismissedOffer || !librarySync.isEnabled) return
        scope.launch {
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

    fun acceptSyncOffer(scope: CoroutineScope) {
        val tracks = _syncOffer.value
        if (tracks.isEmpty() || _isFetching.value) return
        scope.launch {
            _isFetching.value = true
            librarySync.fetchMissing(tracks) { _fetchProgress.value = it }
                .onSuccess { count ->
                    _syncOffer.value = emptyList()
                    _syncCovers.value = emptyList()
                    _syncDetailsOpen.value = false
                    _offerRoute.value = null
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
     */
    fun refreshShareSettings(scope: CoroutineScope) {
        if (!secureStorage.agroConfigured.value) return
        scope.launch {
            val settings = sessionApi.syncedSettings().getOrNull() ?: return@launch
            val domain = settings.shareDomain.orEmpty().takeIf { settings.shareEnabled }.orEmpty()
            secureStorage.setAgroShareSettings(domain, settings.shareHosts.orEmpty())
        }
    }

    /** Local failures worth a snackbar, alongside the repository's own. */
    private val _writeErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun syncErrors(scope: CoroutineScope): SharedFlow<String> =
        merge(_writeErrors.asSharedFlow(), librarySync.errors)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 0)
}
